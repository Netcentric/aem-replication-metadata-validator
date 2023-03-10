[![Build Status](https://img.shields.io/github/actions/workflow/status/Netcentric/aem-replication-metadata-validator/maven.yml?branch=main)](https://github.com/Netcentric/aem-replication-metadata-validator/actions)
[![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0)
[![Maven Central](https://img.shields.io/maven-central/v/biz.netcentric.filevault.validator/aem-replication-metadata-validator)](https://search.maven.org/artifact/biz.netcentric.filevault.validator/aem-replication-metadata-validator)
[![SonarCloud Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-replication-metadata-validator&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Netcentric_aem-replication-metadata-validator)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-replication-metadata-validator&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Netcentric_aem-replication-metadata-validator)


# Overview

Validates that FileVault content packages contain replication metadata (i.e. `cq:lastReplicated` or `cq:lastPublished` property) in certain nodes. Its value must be newer than the last modification of the node. 
This is important for all content packages which are installed on both Author and Publish to make the Author instance aware of the fact that the according page/resource is already active in the latest version. AEM Author checks for outdated and non-published references via implementations of [`com.adobe.granite.references.ReferenceProvider`][4].
Every reference which is not detected as published in the most recent version (i.e. has incorrect metadata) will be [selected for activation along with the referencing page][aem-publish] which is *unnecessary* for nodes already existing on the publish *and often fails* due to missing permissions of the user. In the worst case such references block the replication queue (for immutable references below `/apps` in AEM as a Cloud Service).

This artifact provides a validator implementation for the [FileVault Validation Module][2] and can be used for example with the [filevault-package-maven-plugin][3].

Use cases are

1. [Editable templates][page-templates]' structure nodes (as found by `com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider`)
1. [Editable templates][page-templates]' policy nodes (as found by `com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider`)
1. Generic [Sling Context-Aware configurations][ca-configs] (as found by [`com.adobe.cq.wcm.core.components.internal.services.CaConfigReferenceProvider`](https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java))

Those are validated through the default value for `includedNodePathPatternsAndTypes`. This default set can be overridden through the settings outlined below.

# Settings

The following options are supported apart from the default settings mentioned in [FileVault validation][2].

Option | Mandatory | Description | Default Value | Since Version
--- | --- | --- | --- | ---
`includedNodePathPatternsAndTypes` | no | Comma-separated list of items, where each item has the format `<regex>[<primary-type>]`. The given regular expression must match a given node path (fully) for the node to be checked for metadata. In addition the node must have the given primary type. | `.*/settings/wcm/templates/[^/]*/structure[cq:Page], .*/settings/wcm/templates/[^/]*/policies[cq:Page], /(apps\|conf)/.*/(sling:configs\|settings/cloudconfigs)/.*[cq:Page])` | 1.0.0 
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
[4]: https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/com/adobe/granite/references/ReferenceProvider.html
[page-templates]: https://experienceleague.adobe.com/docs/experience-manager-65/developing/platform/templates/page-templates-editable.html
[ca-configs]: https://sling.apache.org/documentation/bundles/context-aware-configuration/context-aware-configuration.html
[aem-publish]: https://experienceleague.adobe.com/docs/experience-manager-cloud-service/content/sites/authoring/fundamentals/publishing-pages.html
