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
 */

import { schema } from '@osd/config-schema';
import {
  IRouter,
  IOpenSearchDashboardsResponse,
  ResponseError,
  ILegacyScopedClusterClient,
} from '../../../../src/core/server';
import { API_PREFIX } from '../../common';
import { checkErrorType, errorResponse } from './utils/helpers';
import { createReportDefinition } from './lib/createReportDefinition';
import {
  backendToUiReportDefinition,
  backendToUiReportDefinitionsList,
} from './utils/converters/backendToUi';
import { updateReportDefinition } from './lib/updateReportDefinition';
import { DEFAULT_MAX_SIZE } from './utils/constants';
import { addToMetric } from './utils/metricHelper';
import { validateReportDefinition } from '../../server/utils/validationHelper';
import { AccessInfoType } from 'server';

export default function (router: IRouter, accessInfo: AccessInfoType) {
  const { basePath, serverInfo } = accessInfo;
  // Create report Definition
  router.post(
    {
      path: `${API_PREFIX}/reportDefinition`,
      validate: {
        body: schema.any(),
      },
    },
    async (
      context,
      request,
      response
    ): Promise<IOpenSearchDashboardsResponse<any | ResponseError>> => {
      addToMetric('report_definition', 'create', 'count');
      let reportDefinition = request.body;
      //@ts-ignore
      const logger = context.reporting_plugin.logger;
      // input validation
      try {
        reportDefinition.report_params.core_params.origin =
          request.headers.origin;
        reportDefinition = await validateReportDefinition(
          context.core.opensearch.legacy.client,
          reportDefinition,
          basePath
        );
      } catch (error) {
        logger.error(
          `Failed input validation for create report definition ${error}`
        );
        addToMetric('report_definition', 'create', 'user_error');
        return response.badRequest({ body: error });
      }

      // save metadata
      try {
        const res = await createReportDefinition(
          request,
          context,
          reportDefinition
        );

        return response.ok({
          body: {
            state: 'Report definition created',
            scheduler_response: res,
          },
        });
      } catch (error) {
        logger.error(`Failed to create report definition: ${error}`);
        addToMetric('report_definition', 'create', checkErrorType(error));
        return errorResponse(response, error);
      }
    }
  );

  // Update report definition by id
  router.put(
    {
      path: `${API_PREFIX}/reportDefinitions/{reportDefinitionId}`,
      validate: {
        body: schema.any(),
        params: schema.object({
          reportDefinitionId: schema.string(),
        }),
      },
    },
    async (
      context,
      request,
      response
    ): Promise<IOpenSearchDashboardsResponse<any | ResponseError>> => {
      addToMetric('report_definition', 'update', 'count');
      let reportDefinition = request.body;
      //@ts-ignore
      const logger = context.reporting_plugin.logger;
      // input validation
      try {
        reportDefinition.report_params.core_params.origin =
          request.headers.origin;
        reportDefinition = await validateReportDefinition(
          context.core.opensearch.legacy.client,
          reportDefinition,
          basePath
        );
      } catch (error) {
        logger.error(
          `Failed input validation for update report definition ${error}`
        );
        addToMetric('report_definition', 'update', 'user_error');
        return response.badRequest({ body: error });
      }
      // Update report definition metadata
      try {
        const opensearchResp = await updateReportDefinition(
          request,
          context,
          reportDefinition
        );

        return response.ok({
          body: {
            state: 'Report definition updated',
            scheduler_response: opensearchResp,
          },
        });
      } catch (error) {
        logger.error(`Failed to update report definition: ${error}`);
        addToMetric('report_definition', 'update', checkErrorType(error));
        return errorResponse(response, error);
      }
    }
  );

  // get all report definitions details
  router.get(
    {
      path: `${API_PREFIX}/reportDefinitions`,
      validate: {
        query: schema.object({
          fromIndex: schema.maybe(schema.number()),
          maxItems: schema.maybe(schema.number()),
        }),
      },
    },
    async (
      context,
      request,
      response
    ): Promise<IOpenSearchDashboardsResponse<any | ResponseError>> => {
      addToMetric('report_definition', 'list', 'count');
      const { fromIndex, maxItems } = request.query as {
        fromIndex: number;
        maxItems: number;
      };

      try {
        // @ts-ignore
        const opensearchReportsClient: ILegacyScopedClusterClient = context.reporting_plugin.opensearchReportsClient.asScoped(
          request
        );

        const opensearchResp = await opensearchReportsClient.callAsCurrentUser(
          'opensearch_reports.getReportDefinitions',
          {
            fromIndex: fromIndex,
            maxItems: maxItems || DEFAULT_MAX_SIZE,
          }
        );

        const reportDefinitionsList = backendToUiReportDefinitionsList(
          opensearchResp.reportDefinitionDetailsList,
          basePath
        );

        return response.ok({
          body: {
            data: reportDefinitionsList,
          },
        });
      } catch (error) {
        //@ts-ignore
        context.reporting_plugin.logger.error(
          `Failed to get report definition details: ${error}`
        );
        addToMetric('report_definition', 'list', checkErrorType(error));
        return errorResponse(response, error);
      }
    }
  );

  // get report definition detail by id
  router.get(
    {
      path: `${API_PREFIX}/reportDefinitions/{reportDefinitionId}`,
      validate: {
        params: schema.object({
          reportDefinitionId: schema.string(),
        }),
      },
    },
    async (
      context,
      request,
      response
    ): Promise<IOpenSearchDashboardsResponse<any | ResponseError>> => {
      addToMetric('report_definition', 'info', 'count');
      try {
        // @ts-ignore
        const opensearchReportsClient: ILegacyScopedClusterClient = context.reporting_plugin.opensearchReportsClient.asScoped(
          request
        );

        const opensearchResp = await opensearchReportsClient.callAsCurrentUser(
          'opensearch_reports.getReportDefinitionById',
          {
            reportDefinitionId: request.params.reportDefinitionId,
          }
        );

        const reportDefinition = backendToUiReportDefinition(
          opensearchResp.reportDefinitionDetails,
          basePath
        );

        return response.ok({
          body: { report_definition: reportDefinition },
        });
      } catch (error) {
        //@ts-ignore
        context.reporting_plugin.logger.error(
          `Failed to get single report details: ${error}`
        );
        addToMetric('report_definition', 'info', checkErrorType(error));
        return errorResponse(response, error);
      }
    }
  );

  // Delete report definition by id
  router.delete(
    {
      path: `${API_PREFIX}/reportDefinitions/{reportDefinitionId}`,
      validate: {
        params: schema.object({
          reportDefinitionId: schema.string(),
        }),
      },
    },
    async (
      context,
      request,
      response
    ): Promise<IOpenSearchDashboardsResponse<any | ResponseError>> => {
      addToMetric('report_definition', 'delete', 'count');
      try {
        // @ts-ignore
        const opensearchReportsClient: ILegacyScopedClusterClient = context.reporting_plugin.opensearchReportsClient.asScoped(
          request
        );

        const opensearchResp = await opensearchReportsClient.callAsCurrentUser(
          'opensearch_reports.deleteReportDefinitionById',
          {
            reportDefinitionId: request.params.reportDefinitionId,
          }
        );

        return response.ok({
          body: {
            state: 'Report definition deleted',
            opensearch_response: opensearchResp,
          },
        });
      } catch (error) {
        //@ts-ignore
        context.reporting_plugin.logger.error(
          `Failed to delete report definition: ${error}`
        );
        addToMetric('report_definition', 'delete', checkErrorType(error));
        return errorResponse(response, error);
      }
    }
  );
}
