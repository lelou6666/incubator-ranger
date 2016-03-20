/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.common.GUIDUtil;
import org.apache.ranger.common.MessageEnums;
import org.apache.ranger.common.SearchField;
import org.apache.ranger.common.SortField;
import org.apache.ranger.common.SearchField.DATA_TYPE;
import org.apache.ranger.common.SearchField.SEARCH_TYPE;
import org.apache.ranger.common.SortField.SORT_ORDER;
import org.apache.ranger.entity.XXPolicy;
import org.apache.ranger.entity.XXPolicyBase;
import org.apache.ranger.entity.XXService;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.view.RangerPolicyList;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class RangerPolicyServiceBase<T extends XXPolicyBase, V extends RangerPolicy> extends
		RangerBaseModelService<T, V> {

	@Autowired
	GUIDUtil guidUtil;
	
	public RangerPolicyServiceBase() {
		super();
		searchFields.add(new SearchField(SearchFilter.SERVICE_TYPE, "xSvcDef.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL,
				"XXServiceDef xSvcDef, XXService xSvc", "xSvc.type = xSvcDef.id and xSvc.id = obj.service"));
		searchFields.add(new SearchField(SearchFilter.SERVICE_TYPE_ID, "xSvc.type", DATA_TYPE.INTEGER,
				SEARCH_TYPE.FULL, "XXService xSvc", "xSvc.id = obj.service"));
		searchFields.add(new SearchField(SearchFilter.SERVICE_NAME, "xSvc.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL,
				"XXService xSvc", "xSvc.id = obj.service"));
		searchFields.add(new SearchField(SearchFilter.SERVICE_ID, "xSvc.id", DATA_TYPE.INTEGER, SEARCH_TYPE.FULL,
				"XXService xSvc", "xSvc.id = obj.service"));
		searchFields
				.add(new SearchField(SearchFilter.IS_ENABLED, "obj.isEnabled", DATA_TYPE.BOOLEAN, SEARCH_TYPE.FULL));
		searchFields.add(new SearchField(SearchFilter.POLICY_ID, "obj.id", DATA_TYPE.INTEGER, SEARCH_TYPE.FULL));
		searchFields.add(new SearchField(SearchFilter.POLICY_NAME, "obj.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL));
		searchFields.add(new SearchField(SearchFilter.USER, "xUser.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL,
				"XXUser xUser, XXPolicyItem xPolItem, XXPolicyItemUserPerm userPerm", "obj.id = xPolItem.policyId "
						+ "and userPerm.policyItemId = xPolItem.id and xUser.id = userPerm.userId"));
		searchFields.add(new SearchField(SearchFilter.GROUP, "xGrp.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL,
				"XXGroup xGrp, XXPolicyItem xPolItem, XXPolicyItemGroupPerm grpPerm", "obj.id = xPolItem.policyId "
						+ "and grpPerm.policyItemId = xPolItem.id and xGrp.id = grpPerm.groupId"));
		searchFields.add(new SearchField(SearchFilter.POL_RESOURCE, "resMap.value", DATA_TYPE.STRING,
				SEARCH_TYPE.PARTIAL, "XXPolicyResourceMap resMap, XXPolicyResource polRes",
				"resMap.resourceId = polRes.id and polRes.policyId = obj.id"));
		searchFields.add(new SearchField(SearchFilter.POLICY_NAME_PARTIAL, "obj.name", DATA_TYPE.STRING,
				SEARCH_TYPE.PARTIAL));
		searchFields.add(new SearchField(SearchFilter.POLICY_TYPE, "obj.policyType", DATA_TYPE.INTEGER, SEARCH_TYPE.FULL));

		sortFields.add(new SortField(SearchFilter.CREATE_TIME, "obj.createTime"));
		sortFields.add(new SortField(SearchFilter.UPDATE_TIME, "obj.updateTime"));
		sortFields.add(new SortField(SearchFilter.POLICY_ID, "obj.id", true, SORT_ORDER.ASC));
		sortFields.add(new SortField(SearchFilter.POLICY_NAME, "obj.name"));
	}

	@Override
	@SuppressWarnings("unchecked")
	protected XXPolicyBase mapViewToEntityBean(RangerPolicy vObj, XXPolicyBase xObj, int OPERATION_CONTEXT) {
		String guid = (StringUtils.isEmpty(vObj.getGuid())) ? guidUtil.genGUID() : vObj.getGuid();

		xObj.setGuid(guid);
		xObj.setVersion(vObj.getVersion());

		XXService xService = daoMgr.getXXService().findByName(vObj.getService());
		if (xService == null) {
			throw restErrorUtil.createRESTException("No corresponding service found for policyName: " + vObj.getName()
					+ "Service Not Found : " + vObj.getName(), MessageEnums.INVALID_INPUT_DATA);
		}
		xObj.setService(xService.getId());
		xObj.setName(vObj.getName());
		xObj.setPolicyType(vObj.getPolicyType());
		xObj.setDescription(vObj.getDescription());
		xObj.setResourceSignature(vObj.getResourceSignature());
		xObj.setIsAuditEnabled(vObj.getIsAuditEnabled());
		xObj.setIsEnabled(vObj.getIsEnabled());

		return xObj;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected RangerPolicy mapEntityToViewBean(RangerPolicy vObj, XXPolicyBase xObj) {
		XXService xService = daoMgr.getXXService().getById(xObj.getService());
		vObj.setGuid(xObj.getGuid());
		vObj.setVersion(xObj.getVersion());
		vObj.setService(xService.getName());
		vObj.setName(xObj.getName());
		vObj.setPolicyType(xObj.getPolicyType());
		vObj.setDescription(xObj.getDescription());
		vObj.setResourceSignature(xObj.getResourceSignature());
		vObj.setIsEnabled(xObj.getIsEnabled());
		vObj.setIsAuditEnabled(xObj.getIsAuditEnabled());
		return vObj;
	}

	@SuppressWarnings("unchecked")
	public RangerPolicyList searchRangerPolicies(SearchFilter searchFilter) {
		List<RangerPolicy> policyList = new ArrayList<RangerPolicy>();
		RangerPolicyList retList = new RangerPolicyList();

		List<XXPolicy> xPolList = (List<XXPolicy>) searchResources(searchFilter, searchFields, sortFields, retList);
		for (XXPolicy xPol : xPolList) {
			policyList.add(populateViewBean((T) xPol));
		}
		retList.setPolicies(policyList);

		return retList;
	}
}
