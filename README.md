[![Build Status](https://img.shields.io/github/workflow/status/Netcentric/aem-replication-metadata-validator/maven-cicd)](https://github.com/Netcentric/aem-replication-metadata-validator/actions)
[![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0)
[![Maven Central](https://img.shields.io/maven-central/v/biz.netcentric.filevault.validator/aem-replication-metadata-validator)](https://search.maven.org/artifact/biz.netcentric.filevault.validator/aem-replication-metadata-validator)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-cloud-validator&metric=alert_status)](https://sonarcloud.io/dashboard?id=Netcentric_aem-cloud-validator)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-cloud-validator&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=Netcentric_aem-cloud-validator)

# Overview

Validates that content packages contain replication metadata (i.e. `cq:lastReplicated` or `cq:lastPublished` property) in certain nodes which contains a date which is newer than the last modification of the node. 
This is important for all content packages which are installed on both Author and Publish to make the Author instance aware of the fact that the according page/resource is already active in the latest version. AEM Author checks for outdated references via implementations of [`com.day.cq.wcm.api.reference.ReferenceProvider`][4]
It is a validator implementation for the [FileVault Validation Module][2] and can be used for example with the [filevault-package-maven-plugin][3].

Use cases are
1. Editable Templates' structure nodes (as found by `com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider`)
1. Editable Templates' policy nodes (as found by `com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider`)
1. Generic Context-Aware configurations (as found by [`com.adobe.cq.wcm.core.components.internal.services.CaConfigReferenceProvider`](https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java)`)


# Settings

The following options are supported apart from the default settings mentioned in [FileVault validation][2].

Option | Mandatory | Description | Default Value | Since Version
--- | --- | --- | --- | ---
`includedNodePathPatternsAndTypes` | no | Comma-separated list of items, where each item has the format `"<regex>[<primary-type>]`. The given regular expression must match a given node path for the node to be checked for metadata. In addition the node must have the given primary type. | `.*/settings/wcm/[^/]*/initial[cq:Page], .*/settings/wcm/[^/]*/policies[cq:Page], /(apps\|conf)/.*/(sling:configs|settings/cloudconfigs)/.*[cq:Page])` | 1.0.0 
`strictLastModificationDateCheck` | no | `true` means that nodes without a last modification property should always lead to validation errors. Otherwise they are handled in a lenient fashion like AEM behaves (i.e. assumption is that the modification date is -1 which is older than all replication dates). | `false` | 1.0.0

# Usage with Maven

You can use this validator with the [FileVault Package Maven Plugin][3] in version 1.3.0 or higher like this

```
<plugin>
  <groupId>org.apache.jackrabbit</groupId>
  <artifactId>filevault-package-maven-plugin</artifactId>
  <configuration>
    <validatorsSettings>
      <netcentric-aem-replication-metadata>
        <options>
          <strictLastModificationDateCheck>true</strictLastModificationDateCheck><!-- default value is false  -->
        </options>
      </netcentric-aem-replication-metadata>
    </validatorsSettings>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>biz.netcentric.filevault.validator</groupId>
      <artifactId>aem-replication-metadata-validator</artifactId>
      <version><latestversion></version>
    </dependency>
  </dependencies>
</plugin>
```


[aemanalyser-maven-plugin]: https://github.com/adobe/aemanalyser-maven-plugin/tree/main/aemanalyser-maven-plugin
[2]: https://jackrabbit.apache.org/filevault/validation.html
[3]: https://jackrabbit.apache.org/filevault-package-maven-plugin/index.html
[4]: https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/com/day/cq/wcm/api/reference/ReferenceProvider.html
