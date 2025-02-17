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

import React, { useState } from 'react';
import { EuiComboBox, EuiFormRow } from '@elastic/eui';
import { useEffect } from 'react';
import { ReportDeliveryProps } from './delivery';

const OpenSearchDashboardsUserDelivery = (props: ReportDeliveryProps) => {
  const {
    edit,
    editDefinitionId,
    reportDefinitionRequest,
    httpClientProps,
  } = props;

  const options = [];
  const [selectedOptions, setSelected] = useState([]);

  const onChangeDeliveryRecipients = (selectedOptions) => {
    setSelected(selectedOptions);
    reportDefinitionRequest.delivery.delivery_params.opensearch_dashboards_recipients = selectedOptions.map(
      (option) => option.label
    );
  };

  const onCreateDeliveryRecipientOption = (
    searchValue: string,
    flattenedOptions = []
  ) => {
    const normalizedSearchValue = searchValue.trim().toLowerCase();

    if (!normalizedSearchValue) {
      return;
    }

    const newOption = {
      label: searchValue,
    };

    // Create the option if it doesn't exist.
    if (
      flattenedOptions.findIndex(
        (option) => option.label.trim().toLowerCase() === normalizedSearchValue
      ) === -1
    ) {
      options.push(newOption);
    }

    // Select the option.
    onChangeDeliveryRecipients([...selectedOptions, newOption]);
  };

  const defaultEditDeliveryParams = (deliveryParams) => {
    if (deliveryParams.opensearch_dashboards_recipients) {
      reportDefinitionRequest.delivery.delivery_params = {
        opensearch_dashboards_recipients: deliveryParams.opensearch_dashboards_recipients.map(
          (opensearchDashboardsRecipient) =>
            onCreateDeliveryRecipientOption(opensearchDashboardsRecipient, selectedOptions)
        ),
      };
    } else {
      const opensearchDashboardsUserParams = {
        opensearch_dashboards_recipients: selectedOptions,
      };
      reportDefinitionRequest.delivery.delivery_params = opensearchDashboardsUserParams;
    }
  };

  useEffect(() => {
    if (edit) {
      httpClientProps
        .get(`../api/reporting/reportDefinitions/${editDefinitionId}`)
        .then(async (response) => {
          defaultEditDeliveryParams(
            response.report_definition.delivery.delivery_params
          );
        });
    } else {
      reportDefinitionRequest.delivery.delivery_params = {
        opensearch_dashboards_recipients: selectedOptions,
      };
    }
  }, []);

  return (
    <EuiFormRow label="OpenSearch Dashboards recipients" helpText="Select or add users">
      <EuiComboBox
        placeholder="Select or create options"
        options={options}
        selectedOptions={selectedOptions}
        onChange={onChangeDeliveryRecipients}
        onCreateOption={onCreateDeliveryRecipientOption}
        isClearable={true}
        data-test-subj="demoComboBox"
      />
    </EuiFormRow>
  );
};

export { OpenSearchDashboardsUserDelivery };
