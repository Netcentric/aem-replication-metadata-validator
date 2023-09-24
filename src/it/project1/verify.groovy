String buildLog = new File(basedir, 'build.log').text

// application package
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: No replication action set for agent publish: Replication property {http://www.day.com/jcr/cq/1.0}lastReplicationAction not found\", filePath=jcr_root${File.separator}apps${File.separator}example${File.separator}_sling_configs${File.separator}com.adobe.cq.wcm.core.components.internal.DataLayerConfig${File.separator}.content.xml, nodePath=/apps/example/sling:configs/com.adobe.cq.wcm.core.components.internal.DataLayerConfig/jcr:content,") : 'data layer violation not found'
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: No replication date set for agent publish: Replication properties {http://www.day.com/jcr/cq/1.0}lastReplicated or {http://www.day.com/jcr/cq/1.0}lastPublished not found\", filePath=jcr_root${File.separator}apps${File.separator}example${File.separator}_sling_configs${File.separator}com.adobe.cq.wcm.core.components.internal.DataLayerConfig${File.separator}.content.xml, nodePath=/apps/example/sling:configs/com.adobe.cq.wcm.core.components.internal.DataLayerConfig/jcr:content,") : 'data layer violation not found'
assert buildLog.contains('[ERROR] Failed to execute goal org.apache.jackrabbit:filevault-package-maven-plugin:1.3.0:validate-package (default-validate-package) on project application-package: Found 2 violation(s) (with severity=ERROR). Check above errors for details -> [Help 1]') : 'Incorrect number of validation issues found'
// content package
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: The replication date 2019-10-22T20:34:50.533Z for agent publish is older than the last modification date 2020-07-09T15:11:09.069Z\", filePath=jcr_root${File.separator}conf${File.separator}example${File.separator}settings${File.separator}wcm${File.separator}policies${File.separator}.content.xml, nodePath=/conf/example/settings/wcm/policies/wknd/components/container/content-default,") : 'policy violation not found'
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: The replication date 2021-12-31T23:00:00Z for agent publish is older than the last modification date 2022-01-01T23:00:00Z\", filePath=jcr_root${File.separator}conf${File.separator}example${File.separator}settings${File.separator}wcm${File.separator}templates${File.separator}template1${File.separator}structure${File.separator}.content.xml, nodePath=/conf/example/settings/wcm/templates/template1/structure/jcr:content,") : 'template structure violation not found'
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: Last replication action not allowed for this path but is ACTIVATE\", filePath=jcr_root${File.separator}conf${File.separator}example${File.separator}settings${File.separator}wcm${File.separator}templates${File.separator}template1${File.separator}initial${File.separator}.content.xml, nodePath=/conf/example/settings/wcm/templates/template1/initial/jcr:content, line=12, column=19") : 'initial violation not found'
assert buildLog.contains("[ERROR] ValidationViolation: \"netcentric-aem-replication-metadata: Last replication date not allowed for this path but is 2021-12-31T23:00:00Z\", filePath=jcr_root${File.separator}conf${File.separator}example${File.separator}settings${File.separator}wcm${File.separator}templates${File.separator}template1${File.separator}initial${File.separator}.content.xml, nodePath=/conf/example/settings/wcm/templates/template1/initial/jcr:content, line=12, column=19") : 'initial violation not found'
assert buildLog.contains('[ERROR] Failed to execute goal org.apache.jackrabbit:filevault-package-maven-plugin:1.3.0:validate-package (default-validate-package) on project content-package: Found 4 violation(s) (with severity=ERROR). Check above errors for details -> [Help 1]') : 'Incorrect number of validation issues found'
