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
package com.amazon.opendistroforelasticsearch.reportsscheduler.resthandler

import com.amazon.opendistroforelasticsearch.reportsscheduler.ReportsSchedulerPlugin.Companion.BASE_REPORTS_URI
import com.amazon.opendistroforelasticsearch.reportsscheduler.action.PollReportInstanceAction
import com.amazon.opendistroforelasticsearch.reportsscheduler.action.ReportInstanceActions
import com.amazon.opendistroforelasticsearch.reportsscheduler.model.PollReportInstanceRequest
import org.opensearch.client.node.NodeClient
import org.opensearch.rest.BaseRestHandler.RestChannelConsumer
import org.opensearch.rest.BytesRestResponse
import org.opensearch.rest.RestHandler.Route
import org.opensearch.rest.RestRequest
import org.opensearch.rest.RestRequest.Method.GET
import org.opensearch.rest.RestStatus

/**
 * Rest handler for getting list of report instances.
 * This handler uses [ReportInstanceActions].
 */
internal class ReportInstancePollRestHandler : PluginBaseHandler() {
    companion object {
        private const val REPORT_INSTANCE_POLL_ACTION = "report_instance_poll_actions"
        private const val POLL_REPORT_INSTANCE_URL = "$BASE_REPORTS_URI/poll_instance"
    }

    /**
     * {@inheritDoc}
     */
    override fun getName(): String {
        return REPORT_INSTANCE_POLL_ACTION
    }

    /**
     * {@inheritDoc}
     */
    override fun routes(): List<Route> {
        return listOf(
            /**
             * Poll report instances for pending job
             * Request URL: GET POLL_REPORT_INSTANCE_URL
             * Request body: None
             * Response body: Ref [com.amazon.opendistroforelasticsearch.reportsscheduler.model.PollReportInstanceResponse]
             */
            Route(GET, POLL_REPORT_INSTANCE_URL)
        )
    }

    /**
     * {@inheritDoc}
     */
    override fun responseParams(): Set<String> {
        return setOf()
    }

    /**
     * {@inheritDoc}
     */
    override fun executeRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        return when (request.method()) {
            GET -> RestChannelConsumer {
                client.execute(PollReportInstanceAction.ACTION_TYPE,
                    PollReportInstanceRequest(),
                    RestResponseToXContentListener(it))
            }
            else -> RestChannelConsumer {
                it.sendResponse(BytesRestResponse(RestStatus.METHOD_NOT_ALLOWED, "${request.method()} is not allowed"))
            }
        }
    }
}
