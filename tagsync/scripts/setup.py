#!/usr/bin/python
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re
import StringIO
import xml.etree.ElementTree as ET
import ConfigParser
import os,errno,sys,getopt
from os import listdir
from os.path import isfile, join, dirname, basename
from urlparse import urlparse
from time import gmtime, strftime, localtime
from xml import etree
import shutil
import pwd, grp

if (not 'JAVA_HOME' in os.environ):
	print "ERROR: JAVA_HOME environment variable is not defined. Please define JAVA_HOME before running this script"
	sys.exit(1)

debugLevel = 1
generateXML = 0
installPropDirName = '.'
pidFolderName = '/var/run/ranger'
logFolderName = '/var/log/ranger'
initdDirName = '/etc/init.d'

rangerBaseDirName = '/etc/ranger'
tagsyncBaseDirName = 'tagsync'
confBaseDirName = 'conf'
confDistBaseDirName = 'conf.dist'

outputFileName = 'ranger-tagsync-site.xml'
installPropFileName = 'install.properties'
log4jFileName          = 'log4j.xml'
install2xmlMapFileName = 'installprop2xml.properties'
templateFileName = 'ranger-tagsync-template.xml'
initdProgramName = 'ranger-tagsync'
atlasApplicationPropFileName = 'application.properties'

installTemplateDirName = join(installPropDirName,'templates')
confDistDirName = join(installPropDirName, confDistBaseDirName)
tagsyncLogFolderName = join(logFolderName, 'tagsync')
tagsyncBaseDirFullName = join(rangerBaseDirName, tagsyncBaseDirName)
confFolderName = join(tagsyncBaseDirFullName, confBaseDirName)
localConfFolderName = join(installPropDirName, confBaseDirName)

credUpdateClassName =  'org.apache.ranger.credentialapi.buildks'
defaultKeyStoreFileName = '/etc/ranger/tagsync/conf/rangertagsync.jceks'

unixUserProp = 'unix_user'
unixGroupProp = 'unix_group'

logFolderPermMode = 0777
rootOwnerId = 0
initPrefixList = ['S99', 'K00']

TAG_SOURCE_KEY  = 'TAG_SOURCE'
TAGSYNC_ATLAS_KAFKA_ENDPOINTS_KEY = 'TAGSYNC_ATLAS_KAFKA_ENDPOINTS'
TAGSYNC_ATLAS_ZOOKEEPER_ENDPOINT_KEY = 'TAGSYNC_ATLAS_ZOOKEEPER_ENDPOINT'
TAGSYNC_ATLAS_CONSUMER_GROUP_KEY = 'TAGSYNC_ATLAS_CONSUMER_GROUP'
TAGSYNC_ATLAS_TO_RANGER_SERVICE_MAPPING = 'ranger.tagsync.atlas.to.service.mapping'
TAGSYNC_INSTALL_PROP_PREFIX_FOR_ATLAS_RANGER_MAPPING = 'ranger.tagsync.atlas.'
TAGSYNC_ATLAS_CLUSTER_IDENTIFIER = '.instance.'
TAGSYNC_INSTALL_PROP_SUFFIX_FOR_ATLAS_RANGER_MAPPING = '.ranger.service'
TAG_SOURCE_ATLAS = 'atlas'
TAG_SOURCE_ATLASREST = 'atlasrest'
TAG_SOURCE_FILE = 'file'

def archiveFile(originalFileName):
    archiveDir = dirname(originalFileName)
    archiveFileName = "." + basename(originalFileName) + "." + (strftime("%d%m%Y%H%M%S", localtime()))
    movedFileName = join(archiveDir,archiveFileName)
    print "INFO: moving [%s] to [%s] ......." % (originalFileName,movedFileName)
    os.rename(originalFileName, movedFileName)

def getXMLConfigKeys(xmlFileName):
    ret = []
    tree = ET.parse(xmlFileName)
    root = tree.getroot()
    for config in root.iter('property'):
        name = config.find('name').text
        ret.append(name)
    return ret

def getXMLConfigMap(xmlFileName):
    ret = {}
    tree = ET.parse(xmlFileName)
    root = tree.getroot()
    for config in root.findall('property'):
        name = config.find('name').text
        val = config.find('value').text
        ret[name] = val
    return ret


def getPropertiesConfigMap(configFileName):
    ret = {}
    config = StringIO.StringIO()
    config.write('[dummysection]\n')
    config.write(open(configFileName).read())
    config.seek(0,os.SEEK_SET)
    fcp = ConfigParser.ConfigParser()
    fcp.optionxform = str
    fcp.readfp(config)
    for k,v in fcp.items('dummysection'):
        ret[k] = v
    return ret

def getPropertiesKeyList(configFileName):
    ret = []
    config = StringIO.StringIO()
    config.write('[dummysection]\n')
    config.write(open(configFileName).read())
    config.seek(0,os.SEEK_SET)
    fcp = ConfigParser.ConfigParser()
    fcp.optionxform = str
    fcp.readfp(config)
    for k,v in fcp.items('dummysection'):
        ret.append(k)
    return ret

def writeXMLUsingProperties(xmlTemplateFileName,prop,xmlOutputFileName):
    tree = ET.parse(xmlTemplateFileName)
    root = tree.getroot()
    for config in root.findall('property'):
        name = config.find('name').text
        if (name in prop.keys()):
			if (name == TAGSYNC_ATLAS_TO_RANGER_SERVICE_MAPPING):
				# Expected value is 'clusterName,componentName,serviceName;clusterName,componentName,serviceName' ...
				# Blanks are not supported anywhere in the value.
				valueString = str(prop[name])
				if valueString and valueString.strip():
					multiValues = valueString.split(';')
					listLen = len(multiValues)
					index = 0
					while index < listLen:
						parts = multiValues[index].split(',')
						if len(parts) == 3:
							newConfig = ET.SubElement(root, 'property')
							newName = ET.SubElement(newConfig, 'name')
							newValue = ET.SubElement(newConfig, 'value')
							newName.text = TAGSYNC_INSTALL_PROP_PREFIX_FOR_ATLAS_RANGER_MAPPING + str(parts[1]) + TAGSYNC_ATLAS_CLUSTER_IDENTIFIER + str(parts[0]) + TAGSYNC_INSTALL_PROP_SUFFIX_FOR_ATLAS_RANGER_MAPPING
							newValue.text = str(parts[2])
						else:
							print "ERROR: incorrect syntax for %s, value=%s" % (TAGSYNC_ATLAS_TO_RANGER_SERVICE_MAPPING, multiValues[index])
						index += 1
				root.remove(config)
			else:
				config.find('value').text = str(prop[name])
        #else:
        #    print "ERROR: key not found: %s" % (name)
    if isfile(xmlOutputFileName):
        archiveFile(xmlOutputFileName)
    tree.write(xmlOutputFileName)

def updatePropertyInJCKSFile(jcksFileName,propName,value):
	fn = jcksFileName
	if (value == ''):
		value = ' '
	cmd = "java -cp './lib/*' %s create '%s' -value '%s' -provider jceks://file%s 2>&1" % (credUpdateClassName,propName,value,fn)
	ret = os.system(cmd)
	if (ret != 0):
		print "ERROR: Unable update the JCKSFile(%s) for aliasName (%s)" % (fn,propName)
		sys.exit(1)
	return ret

def convertInstallPropsToXML(props):
	directKeyMap = getPropertiesConfigMap(join(installTemplateDirName,install2xmlMapFileName))
	ret = {}
	atlasOutFn = join(confFolderName, atlasApplicationPropFileName)

	atlasOutFile = file(atlasOutFn, "w")

	for k,v in props.iteritems():
		if (k in directKeyMap.keys()):
			newKey = directKeyMap[k]
			if (k == TAGSYNC_ATLAS_KAFKA_ENDPOINTS_KEY):
				atlasOutFile.write(newKey + "=" + v + "\n")
			elif (k == TAGSYNC_ATLAS_ZOOKEEPER_ENDPOINT_KEY):
				atlasOutFile.write(newKey + "=" + v + "\n")
			elif (k == TAGSYNC_ATLAS_CONSUMER_GROUP_KEY):
				atlasOutFile.write(newKey + "=" + v + "\n")
			else:
				ret[newKey] = v
		else:
			print "Direct Key not found:%s" % (k)

	ret['ranger.tagsync.sink.impl.class'] = 'org.apache.ranger.tagsync.sink.tagadmin.TagAdminRESTSink'

	if (TAG_SOURCE_KEY in ret):
		ret['ranger.tagsync.source.impl.class'] = ret[TAG_SOURCE_KEY]
		del ret[TAG_SOURCE_KEY]

	atlasOutFile.close()

	return ret

def createUser(username,groupname):
	cmd = "useradd -g %s %s -m" % (groupname,username)
	ret = os.system(cmd)
	if (ret != 0):
		print "ERROR: os command execution (%s) failed. error code = %d " % (cmd, ret)
		sys.exit(1)
	try:
		ret = pwd.getpwnam(username).pw_uid
		return ret
	except KeyError, e:
		print "ERROR: Unable to create a new user account: %s with group %s - error [%s]" % (username,groupname,e)
		sys.exit(1)

def createGroup(groupname):
	cmd = "groupadd %s" % (groupname)
	ret = os.system(cmd)
	if (ret != 0):
		print "ERROR: os command execution (%s) failed. error code = %d " % (cmd, ret)
		sys.exit(1)
	try:
		ret = grp.getgrnam(groupname).gr_gid
		return ret
	except KeyError, e:
		print "ERROR: Unable to create a new group: %s" % (groupname,e)
		sys.exit(1)

def initializeInitD():
	if (os.path.isdir(initdDirName)):
		fn = join(installPropDirName,initdProgramName)
		initdFn = join(initdDirName,initdProgramName)
		shutil.copy(fn, initdFn)
		os.chmod(initdFn,0550)
		rcDirList = [ "/etc/rc2.d", "/etc/rc3.d", "/etc/rc.d/rc2.d", "/etc/rc.d/rc3.d" ]
		for rcDir in rcDirList:
			if (os.path.isdir(rcDir)):
				for  prefix in initPrefixList:
					scriptFn = prefix + initdProgramName
					scriptName = join(rcDir, scriptFn)
					if isfile(scriptName):
						os.remove(scriptName)
					#print "+ ln -sf %s %s" % (initdFn, scriptName)
					os.symlink(initdFn,scriptName)
		tagSyncScriptName = "ranger-tagsync-services.sh"
		localScriptName = os.path.abspath(join(installPropDirName,tagSyncScriptName))
		ubinScriptName = join("/usr/bin",tagSyncScriptName)
		if isfile(ubinScriptName):
			os.remove(ubinScriptName)
		os.symlink(localScriptName,ubinScriptName)


def main():

	print "\nINFO: Installing ranger-tagsync .....\n"

	dirList = [ rangerBaseDirName, tagsyncBaseDirFullName, confFolderName ]
	for dir in dirList:
		if (not os.path.isdir(dir)):
			os.makedirs(dir,0755)

	defFileList = [ log4jFileName ]
	for defFile in defFileList:
		fn = join(confDistDirName, defFile)
		if ( isfile(fn) ):
			shutil.copy(fn,join(confFolderName,defFile))

	#
	# Create JAVA_HOME setting in confFolderName
	#
	java_home_setter_fn = join(confFolderName, 'java_home.sh')
	if isfile(java_home_setter_fn):
		archiveFile(java_home_setter_fn)
	jhf = open(java_home_setter_fn, 'w')
	str = "export JAVA_HOME=%s\n" % os.environ['JAVA_HOME']
	jhf.write(str)
	jhf.close()
	os.chmod(java_home_setter_fn,0750)


	if (not os.path.isdir(localConfFolderName)):
		os.symlink(confFolderName, localConfFolderName)

	installProps = getPropertiesConfigMap(join(installPropDirName,installPropFileName))
	modifiedInstallProps = convertInstallPropsToXML(installProps)

	mergeProps = {}
	mergeProps.update(modifiedInstallProps)

	localLogFolderName = mergeProps['ranger.tagsync.logdir']
	if (not os.path.isdir(localLogFolderName)):
		if (localLogFolderName != tagsyncLogFolderName):
			os.symlink(tagsyncLogFolderName, localLogFolderName)

	fn = join(installTemplateDirName,templateFileName)
	outfn = join(confFolderName, outputFileName)

	atlasOutFn = join(confFolderName, atlasApplicationPropFileName)

	atlasOutFile = file(atlasOutFn, "a")

	atlasOutFile.write("atlas.notification.embedded=false" + "\n")
	atlasOutFile.write("atlas.kafka.acks=1" + "\n")
	atlasOutFile.write("atlas.kafka.data=${sys:atlas.home}/data/kafka" + "\n")
	atlasOutFile.write("atlas.kafka.hook.group.id=atlas" + "\n")

	atlasOutFile.close()


	if ( os.path.isdir(logFolderName) ):
		logStat = os.stat(logFolderName)
		logStat.st_uid
		logStat.st_gid
		ownerName = pwd.getpwuid(logStat.st_uid).pw_name
		groupName = pwd.getpwuid(logStat.st_uid).pw_name
	else:
		os.makedirs(logFolderName,logFolderPermMode)

	if (not os.path.isdir(pidFolderName)):
		os.makedirs(pidFolderName,logFolderPermMode)

	if (not os.path.isdir(tagsyncLogFolderName)):
		os.makedirs(tagsyncLogFolderName,logFolderPermMode)

	if (unixUserProp in mergeProps):
		ownerName = mergeProps[unixUserProp]
	else:
		mergeProps[unixUserProp] = "ranger"
		ownerName = mergeProps[unixUserProp]

	if (unixGroupProp in mergeProps):
		groupName = mergeProps[unixGroupProp]
	else:
		mergeProps[unixGroupProp] = "ranger"
		groupName = mergeProps[unixGroupProp]

	try:
		ownerId = pwd.getpwnam(ownerName).pw_uid
	except KeyError, e:
		ownerId = createUser(ownerName, groupName)

	try:
		groupId = grp.getgrnam(groupName).gr_gid
	except KeyError, e:
		groupId = createGroup(groupId)

	os.chown(logFolderName,ownerId,groupId)
	os.chown(tagsyncLogFolderName,ownerId,groupId)
	os.chown(pidFolderName,ownerId,groupId)
	os.chown(rangerBaseDirName,ownerId,groupId)

	initializeInitD()

	tagsyncKSPath = mergeProps['ranger.tagsync.tagadmin.keystore']

	if (tagsyncKSPath == ''):
		mergeProps['ranger.tagsync.tagadmin.password'] = 'rangertagsync'

	else:
		tagadminPasswd = 'rangertagsync'
		tagadminAlias = 'tagadmin.user.password'
		mergeProps['ranger.tagsync.tagadmin.alias'] = tagadminAlias
		updatePropertyInJCKSFile(tagsyncKSPath,tagadminAlias,tagadminPasswd)
		os.chown(tagsyncKSPath,ownerId,groupId)

	writeXMLUsingProperties(fn, mergeProps, outfn)

	fixPermList = [ ".", tagsyncBaseDirName, confFolderName ]

	for dir in fixPermList:
		for root, dirs, files in os.walk(dir):
			os.chown(root, ownerId, groupId)
			os.chmod(root,0755)
			for obj in dirs:
				dn = join(root,obj)
				os.chown(dn, ownerId, groupId)
				os.chmod(dn, 0755)
			for obj in files:
				fn = join(root,obj)
				os.chown(fn, ownerId, groupId)
				os.chmod(fn, 0755)

	print "\nINFO: Completed ranger-tagsync installation.....\n"

main()
