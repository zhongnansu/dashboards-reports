/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.amazon.opendistroforelasticsearch.reportsscheduler.index

import com.amazon.opendistroforelasticsearch.reportsscheduler.ReportsSchedulerPlugin.Companion.LOG_PREFIX
import com.amazon.opendistroforelasticsearch.reportsscheduler.metrics.Metrics
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.ReportDefinitionDetails
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.ReportDefinitionDetailsSearchResults
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.RestTag.ACCESS_LIST_FIELD
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.RestTag.TENANT_FIELD
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.RestTag.UPDATED_TIME_FIELD
import com.amazon.opendistroforelasticsearch.reportsscheduler.settings.PluginSettings
import com.amazon.opendistroforelasticsearch.reportsscheduler.util.SecureIndexClient
import com.amazon.opendistroforelasticsearch.reportsscheduler.util.logger
import org.opensearch.ResourceAlreadyExistsException
import org.opensearch.action.DocWriteResponse
import org.opensearch.action.admin.indices.create.CreateIndexRequest
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.index.IndexRequest
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.update.UpdateRequest
import org.opensearch.client.Client
import org.opensearch.cluster.service.ClusterService
import org.opensearch.common.unit.TimeValue
import org.opensearch.common.xcontent.LoggingDeprecationHandler
import org.opensearch.common.xcontent.NamedXContentRegistry
import org.opensearch.common.xcontent.XContentType
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import java.util.concurrent.TimeUnit

/**
 * Class for doing ES index operation to maintain report definitions in cluster.
 */
internal object ReportDefinitionsIndex {
    private val log by logger(ReportDefinitionsIndex::class.java)
    const val REPORT_DEFINITIONS_INDEX_NAME = ".opendistro-reports-definitions"
    private const val REPORT_DEFINITIONS_MAPPING_FILE_NAME = "report-definitions-mapping.yml"
    private const val REPORT_DEFINITIONS_SETTINGS_FILE_NAME = "report-definitions-settings.yml"
    private const val MAPPING_TYPE = "_doc"

    private lateinit var client: Client
    private lateinit var clusterService: ClusterService

    /**
     * Initialize the class
     * @param client The ES client
     * @param clusterService The ES cluster service
     */
    fun initialize(client: Client, clusterService: ClusterService) {
        this.client = SecureIndexClient(client)
        this.clusterService = clusterService
    }

    /**
     * Create index using the mapping and settings defined in resource
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createIndex() {
        if (!isIndexExists()) {
            val classLoader = ReportDefinitionsIndex::class.java.classLoader
            val indexMappingSource = classLoader.getResource(REPORT_DEFINITIONS_MAPPING_FILE_NAME)?.readText()!!
            val indexSettingsSource = classLoader.getResource(REPORT_DEFINITIONS_SETTINGS_FILE_NAME)?.readText()!!
            val request = CreateIndexRequest(REPORT_DEFINITIONS_INDEX_NAME)
                .mapping(MAPPING_TYPE, indexMappingSource, XContentType.YAML)
                .settings(indexSettingsSource, XContentType.YAML)
            try {
                val actionFuture = client.admin().indices().create(request)
                val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
                if (response.isAcknowledged) {
                    log.info("$LOG_PREFIX:Index $REPORT_DEFINITIONS_INDEX_NAME creation Acknowledged")
                } else {
                    Metrics.REPORT_DEFINITION_CREATE_SYSTEM_ERROR.counter.increment()
                    throw IllegalStateException("$LOG_PREFIX:Index $REPORT_DEFINITIONS_INDEX_NAME creation not Acknowledged")
                }
            } catch (exception: Exception) {
                if (exception !is ResourceAlreadyExistsException && exception.cause !is ResourceAlreadyExistsException) {
                    Metrics.REPORT_DEFINITION_CREATE_SYSTEM_ERROR.counter.increment()
                    throw exception
                }
            }
        }
    }

    /**
     * Check if the index is created and available.
     * @return true if index is available, false otherwise
     */
    private fun isIndexExists(): Boolean {
        val clusterState = clusterService.state()
        return clusterState.routingTable.hasIndex(REPORT_DEFINITIONS_INDEX_NAME)
    }

    /**
     * create a new doc for reportDefinitionDetails
     * @param reportDefinitionDetails the Report definition details
     * @return ReportDefinition.id if successful, null otherwise
     * @throws java.util.concurrent.ExecutionException with a cause
     */
    fun createReportDefinition(reportDefinitionDetails: ReportDefinitionDetails): String? {
        createIndex()
        val indexRequest = IndexRequest(REPORT_DEFINITIONS_INDEX_NAME)
            .source(reportDefinitionDetails.toXContent())
            .create(true)
        val actionFuture = client.index(indexRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return if (response.result != DocWriteResponse.Result.CREATED) {
            Metrics.REPORT_DEFINITION_CREATE_SYSTEM_ERROR.counter.increment()
            log.warn("$LOG_PREFIX:createReportDefinition - response:$response")
            null
        } else {
            response.id
        }
    }

    /**
     * Query index for report definition ID
     * @param id the id for the document
     * @return Report definition details on success, null otherwise
     */
    fun getReportDefinition(id: String): ReportDefinitionDetails? {
        createIndex()
        val getRequest = GetRequest(REPORT_DEFINITIONS_INDEX_NAME).id(id)
        val actionFuture = client.get(getRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return if (response.sourceAsString == null) {
            log.warn("$LOG_PREFIX:getReportDefinition - $id not found; response:$response")
            null
        } else {
            val parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                response.sourceAsString)
            parser.nextToken()
            ReportDefinitionDetails.parse(parser, id)
        }
    }

    /**
     * Query index for report definition for given access details
     * @param tenant the tenant of the user
     * @param access the list of access details to search reports for.
     * @param from the paginated start index
     * @param maxItems the max items to query
     * @return search result of Report definition details
     */
    fun getAllReportDefinitions(tenant: String, access: List<String>, from: Int, maxItems: Int): ReportDefinitionDetailsSearchResults {
        createIndex()
        val sourceBuilder = SearchSourceBuilder()
            .timeout(TimeValue(PluginSettings.operationTimeoutMs, TimeUnit.MILLISECONDS))
            .sort(UPDATED_TIME_FIELD)
            .size(maxItems)
            .from(from)
        val tenantQuery = QueryBuilders.termsQuery(TENANT_FIELD, tenant)
        if (access.isNotEmpty()) {
            val accessQuery = QueryBuilders.termsQuery(ACCESS_LIST_FIELD, access)
            val query = QueryBuilders.boolQuery()
            query.filter(tenantQuery)
            query.filter(accessQuery)
            sourceBuilder.query(query)
        } else {
            sourceBuilder.query(tenantQuery)
        }
        val searchRequest = SearchRequest()
            .indices(REPORT_DEFINITIONS_INDEX_NAME)
            .source(sourceBuilder)
        val actionFuture = client.search(searchRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        val result = ReportDefinitionDetailsSearchResults(from.toLong(), response)
        log.info("$LOG_PREFIX:getAllReportDefinitions from:$from, maxItems:$maxItems," +
            " retCount:${result.objectList.size}, totalCount:${result.totalHits}")
        return result
    }

    /**
     * update Report definition details for given id
     * @param id the id for the document
     * @param reportDefinitionDetails the Report definition details data
     * @return true if successful, false otherwise
     */
    fun updateReportDefinition(id: String, reportDefinitionDetails: ReportDefinitionDetails): Boolean {
        createIndex()
        val updateRequest = UpdateRequest()
            .index(REPORT_DEFINITIONS_INDEX_NAME)
            .id(id)
            .doc(reportDefinitionDetails.toXContent())
            .fetchSource(true)
        val actionFuture = client.update(updateRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.UPDATED) {
            Metrics.REPORT_DEFINITION_UPDATE_SYSTEM_ERROR.counter.increment()
            log.warn("$LOG_PREFIX:updateReportDefinition failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.UPDATED
    }

    /**
     * delete Report definition details for given id
     * @param id the id for the document
     * @return true if successful, false otherwise
     */
    fun deleteReportDefinition(id: String): Boolean {
        createIndex()
        val deleteRequest = DeleteRequest()
            .index(REPORT_DEFINITIONS_INDEX_NAME)
            .id(id)
        val actionFuture = client.delete(deleteRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.DELETED) {
            Metrics.REPORT_DEFINITION_DELETE_SYSTEM_ERROR.counter.increment()
            log.warn("$LOG_PREFIX:deleteReportDefinition failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.DELETED
    }
}
