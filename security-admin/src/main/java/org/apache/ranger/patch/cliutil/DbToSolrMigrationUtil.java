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

package org.apache.ranger.patch.cliutil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.entity.XXAccessAudit;
import org.apache.ranger.patch.BaseLoader;
import org.apache.ranger.solr.SolrAccessAuditsService;
import org.apache.ranger.authorization.utils.StringUtil;
import org.apache.ranger.common.DateUtil;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.util.CLIUtil;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DbToSolrMigrationUtil extends BaseLoader {
	private static Logger logger = Logger.getLogger(DbToSolrMigrationUtil.class);
	private HttpSolrServer solrServer=null;
	private final static String CHECK_FILE_NAME = "migration_check_file.txt";
	private final static Charset ENCODING = StandardCharsets.UTF_8;
	@Autowired
	RangerDaoManager daoManager;
	@Autowired
	SolrAccessAuditsService solrAccessAuditsService;

	public static void main(String[] args) {
		logger.info("main()");
		try {
			DbToSolrMigrationUtil loader = (DbToSolrMigrationUtil) CLIUtil
					.getBean(DbToSolrMigrationUtil.class);

			loader.init();
			while (loader.isMoreToProcess()) {
				loader.load();
			}
			logger.info("Load complete. Exiting!!!");
			System.exit(0);
		} catch (Exception e) {
			logger.error("Error loading", e);
			System.exit(1);
		}
	}

	@Override
	public void init() throws Exception {
		String solrURL=PropertiesUtil.getProperty("ranger.audit.solr.urls");
		logger.info("solrURL:"+solrURL);
		solrServer = new HttpSolrServer(solrURL);
	}

	@Override
	public void execLoad() {
		logger.info("==> DbToSolrMigrationUtil.execLoad() Start.");
		migrateAuditDbLogsToSolr();
		logger.info("<== DbToSolrMigrationUtil.execLoad() End.");
	}

	public void migrateAuditDbLogsToSolr() {
		long maxXXAccessAuditID = daoManager.getXXAccessAudit().getMaxIdOfXXAccessAudit();
		if(maxXXAccessAuditID==0){
			logger.info("Access Audit log does not exist.");
			return;
		}
		long maxMigratedID=0;
		try {
			maxMigratedID = readMigrationStatusFile(CHECK_FILE_NAME);
		} catch (IOException ex) {
			logger.error("Failed to read migration status from file " + CHECK_FILE_NAME, ex);
		}
		logger.info("ID of the last available audit log: "+ maxXXAccessAuditID);
		if(maxMigratedID > 0) {
			  logger.info("ID of the last migrated audit log: "+ maxMigratedID);
		}
		if(maxMigratedID>=maxXXAccessAuditID){
			logger.info("No more DB Audit logs to migrate. Last migrated audit log ID: " + maxMigratedID);
			return;
		}
		long maxRowsPerBatch=10000;
		//To ceil the actual division result i.e noOfBatches=maxXXAccessAuditID/maxRowsPerBatch
		long noOfBatches=((maxXXAccessAuditID-maxMigratedID)+maxRowsPerBatch-1)/maxRowsPerBatch;
		long rangeStart=maxMigratedID;
		long rangeEnd=maxXXAccessAuditID-maxMigratedID<=maxRowsPerBatch ? maxXXAccessAuditID : rangeStart+maxRowsPerBatch;
		long startTimeInMS=0;
		long timeTaken=0;
		List<XXAccessAudit> xXAccessAuditList=null;
		for(long index=1;index<=noOfBatches;index++){
			logger.info("Batch "+ index+" of total "+noOfBatches);
			startTimeInMS=System.currentTimeMillis();
			//rangeStart and rangeEnd both exclusive, if we add +1 in maxRange
			xXAccessAuditList=daoManager.getXXAccessAudit().getByIdRange(rangeStart,rangeEnd+1);
			for(XXAccessAudit xXAccessAudit:xXAccessAuditList){
				if(xXAccessAudit!=null){
					try {
						send2solr(xXAccessAudit);
					} catch (Throwable e) {
						logger.error("Error while writing audit log id '"+xXAccessAudit.getId()+"' to Solr.", e);
						writeMigrationStatusFile(xXAccessAudit.getId(),CHECK_FILE_NAME);
						logger.info("Stopping migration process!");
						return;
					}
				}
			}
			timeTaken=(System.currentTimeMillis()-startTimeInMS);
			logger.info("Batch #" + index + ": time taken:"+timeTaken+" ms");
			if(rangeEnd<maxXXAccessAuditID){
				writeMigrationStatusFile(rangeEnd,CHECK_FILE_NAME);
			}else{
				writeMigrationStatusFile(maxXXAccessAuditID,CHECK_FILE_NAME);
			}
			rangeStart=rangeEnd;
			rangeEnd=rangeEnd+maxRowsPerBatch;
		}
		
	}

	public void send2solr(XXAccessAudit xXAccessAudit) throws Throwable {
		boolean uidIsString = true;
		SolrInputDocument document = new SolrInputDocument();
		document.addField("id", xXAccessAudit.getId());
		document.addField("access", xXAccessAudit.getAccessType());
		document.addField("enforcer", xXAccessAudit.getAclEnforcer());
		document.addField("agent", xXAccessAudit.getAgentId());
		document.addField("repo", xXAccessAudit.getRepoName());
		document.addField("sess", xXAccessAudit.getSessionId());
		document.addField("reqUser", xXAccessAudit.getRequestUser());
		document.addField("reqData", xXAccessAudit.getRequestData());
		document.addField("resource", xXAccessAudit.getResourcePath());
		document.addField("cliIP", xXAccessAudit.getClientIP());
		document.addField("logType", "RangerAudit");
		document.addField("result", xXAccessAudit.getAccessResult());
		document.addField("policy", xXAccessAudit.getPolicyId());
		document.addField("repoType", xXAccessAudit.getRepoType());
		document.addField("resType", xXAccessAudit.getResourceType());
		document.addField("reason", xXAccessAudit.getResultReason());
		document.addField("action", xXAccessAudit.getAction());
		document.addField("evtTime", DateUtil.getLocalDateForUTCDate(xXAccessAudit.getEventTime()));
		document.addField("seq_num", xXAccessAudit.getSequenceNumber());
		document.addField("event_count", xXAccessAudit.getEventCount());
		document.addField("event_dur_ms", xXAccessAudit.getEventDuration());
		document.addField("tags", xXAccessAudit.getTags());
		//If ID is not set, then we should add it.
		SolrInputField idField = document.getField("id");
		if( idField == null) {
			Object uid = null;
			if(uidIsString) {
				uid = UUID.randomUUID().toString();
			}
			document.setField("id", uid);
		}

		UpdateResponse response = solrServer.add(document);
		if (response.getStatus() != 0) {
			logger.info("Response=" + response.toString() + ", status= "
					+ response.getStatus() + ", event=" + xXAccessAudit.toString());
			throw new Exception("Failed to send audit event ID=" + xXAccessAudit.getId());
		}
	}

	private Long readMigrationStatusFile(String aFileName) throws IOException {
		Long migratedDbID=0L;
		Path path = Paths.get(aFileName);
		if (Files.exists(path) && Files.isRegularFile(path)) {
			List<String> fileContents=Files.readAllLines(path, ENCODING);
			if(fileContents!=null && fileContents.size()>=1){
				String line=fileContents.get(fileContents.size()-1).trim();
				if(!StringUtil.isEmpty(line)){
					try{
						migratedDbID=Long.parseLong(line);
					}catch(Exception ex){
					}
				}
			}
		}
	   return migratedDbID;
	}

	private void writeMigrationStatusFile(Long DbID, String aFileName) {
		try{
			Path path = Paths.get(aFileName);
			List<String> fileContents=new ArrayList<String>();
			fileContents.add(String.valueOf(DbID));
			Files.write(path, fileContents, ENCODING);
		}catch(IOException ex){
			logger.error("Failed to update migration status to file " + CHECK_FILE_NAME, ex);
		}catch(Exception ex){
			logger.error("Error while updating migration status to file " + CHECK_FILE_NAME, ex);
		}
	}
	@Override
	public void printStats() {
	}
}
