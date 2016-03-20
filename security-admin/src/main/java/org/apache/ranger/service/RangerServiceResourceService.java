/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.ranger.common.SearchField;
import org.apache.ranger.common.SearchField.DATA_TYPE;
import org.apache.ranger.common.SearchField.SEARCH_TYPE;
import org.apache.ranger.entity.XXServiceResource;
import org.apache.ranger.plugin.model.RangerServiceResource;
import org.apache.ranger.plugin.util.SearchFilter;
import org.springframework.stereotype.Service;

@Service
public class RangerServiceResourceService extends RangerServiceResourceServiceBase<XXServiceResource, RangerServiceResource> {

	public RangerServiceResourceService() {
		searchFields.add(new SearchField(SearchFilter.TAG_RESOURCE_ID, "obj.id", DATA_TYPE.INTEGER, SEARCH_TYPE.FULL));
		searchFields.add(new SearchField(SearchFilter.TAG_SERVICE_ID, "obj.serviceId", DATA_TYPE.INTEGER, SEARCH_TYPE.FULL));
		searchFields.add(new SearchField(SearchFilter.TAG_RESOURCE_SIGNATURE, "obj.resourceSignature", DATA_TYPE.STRING, SEARCH_TYPE.FULL));
	}

	@Override
	protected void validateForCreate(RangerServiceResource vObj) {

	}

	@Override
	protected void validateForUpdate(RangerServiceResource vObj, XXServiceResource entityObj) {

	}

	@Override
	public RangerServiceResource postUpdate(XXServiceResource resource) {
		RangerServiceResource ret = super.postUpdate(resource);

		daoMgr.getXXService().updateServiceForServiceResourceUpdate(resource.getId(), resource.getUpdateTime());

		return ret;
	}
	
	public RangerServiceResource getPopulatedViewObject(XXServiceResource xObj) {
		return populateViewBean(xObj);
	}

	public RangerServiceResource getServiceResourceByGuid(String guid) {
		RangerServiceResource ret = null;

		XXServiceResource xxServiceResource = daoMgr.getXXServiceResource().findByGuid(guid);
		
		if(xxServiceResource != null) {
			ret = populateViewBean(xxServiceResource);
		}

		return ret;
	}

	public List<RangerServiceResource> getByServiceId(Long serviceId) {
		List<RangerServiceResource> ret = new ArrayList<RangerServiceResource>();

		List<XXServiceResource> xxServiceResources = daoMgr.getXXServiceResource().findByServiceId(serviceId);

		if(CollectionUtils.isNotEmpty(xxServiceResources)) {
			for(XXServiceResource xxServiceResource : xxServiceResources) {
				RangerServiceResource serviceResource = populateViewBean(xxServiceResource);

				ret.add(serviceResource);
			}
		}

		return ret;
	}

	public RangerServiceResource getByResourceSignature(String resourceSignature) {
		RangerServiceResource ret = null;

		XXServiceResource xxServiceResource = daoMgr.getXXServiceResource().findByResourceSignature(resourceSignature);
		
		if(xxServiceResource != null) {
			ret = populateViewBean(xxServiceResource);
		}

		return ret;
	}

	public List<RangerServiceResource> getTaggedResourcesInServiceId(Long serviceId) {
		List<RangerServiceResource> ret = new ArrayList<RangerServiceResource>();

		List<XXServiceResource> xxServiceResources = daoMgr.getXXServiceResource().findTaggedResourcesInServiceId(serviceId);
		
		if(CollectionUtils.isNotEmpty(xxServiceResources)) {
			for(XXServiceResource xxServiceResource : xxServiceResources) {
				RangerServiceResource serviceResource = populateViewBean(xxServiceResource);

				ret.add(serviceResource);
			}
		}

		return ret;
	}
}
