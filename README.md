[![Build Status](https://img.shields.io/github/actions/workflow/status/Netcentric/aem-replication-metadata-validator/maven.yml?branch=main)](https://github.com/Netcentric/aem-replication-metadata-validator/actions)
[![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0)
[![Maven Central](https://img.shields.io/maven-central/v/biz.netcentric.filevault.validator/aem-replication-metadata-validator)](https://search.maven.org/artifact/biz.netcentric.filevault.validator/aem-replication-metadata-validator)
[![SonarCloud Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-replication-metadata-validator&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Netcentric_aem-replication-metadata-validator)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_aem-replication-metadata-validator&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Netcentric_aem-replication-metadata-validator)


# Overview

Validates that FileVault content packages contain replication metadata (`cq:lastReplicationAction` and optionally `cq:lastReplicated`/`cq:lastPublished` properties) in certain nodes. The last action must have value `Activate` and the value of the `cq:lastReplicated`/`cq:lastPublished` property must be newer than the last modification (or in some cases the created) date property of the node (the last modification date is optional, but the last replication date is mandatory).
This is important for all content packages which are installed on both Author and Publish to make the Author instance aware of the fact that the according page/resource is already active in the latest version. AEM Author checks for outdated and non-published references via implementations of [`com.day.cq.wcm.api.reference.ReferenceProvider`][4].
Every reference which is not detected as published in the most recent version (i.e. has missing/incorrect metadata) will be [selected for activation along with the referencing page][aem-publish] which is *unnecessary* for nodes already existing on the publish *and often fails* due to missing permissions of the user. In the worst case such references block the replication queue (for immutable references below `/apps` in AEM as a Cloud Service).

For AEM as a Cloud Service the author service maintains separate replication statuses for publish and preview tiers. Those are captured in a `cq:lastReplicated_preview`/`cq:lastReplicationAction_preview` property for the [preview tier][preview-tier], while for the publish tier they are captured in both `cq:lastReplicated`/`cq:lastReplicationAction` and `cq:lastReplicated_publish`/`cq:lastReplicationAction_publish` (the properties with and without agent suffix are carrying the same value, but only the former is evaluated for detecting the replication status on the publish tier).

# Implementation

This artifact provides a validator implementation for the [FileVault Validation Module][2] and can be used for example with the [filevault-package-maven-plugin][3].

The following repository locations are considered by default which must contain replication metadata indicating that the node is *published and not modified*:

1. [Editable templates][page-templates]' structure nodes (as found by `com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider`)
1. [Editable templates][page-templates]' policy nodes (as found by `com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider`), this includes both *policy mappings* (with resource type=`wcm/core/components/policies/mappings`) as well as *actual policies* (with resource type=`wcm/core/components/policy/policy`). The latter are also found outside actual editable templates.
1. Generic [Sling Context-Aware configurations][ca-configs] (as found by [`com.adobe.cq.wcm.core.components.internal.services.CaConfigReferenceProvider`](https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java))
1. [Segment Pages][segment-pages] (as found by `com.day.cq.personalization.impl.TargetedComponentReferenceProvider`)
1. [Content Fragment Models][content-fragment-models] (as found by `com.adobe.cq.dam.cfm.impl.search.ContentFragmentReferencePublishProvider`)

Those locations are given through the default value for `includedNodePathPatternsAndTypes`. This default set can be overridden through the settings outlined below to check for other nodes.

In addition the following repository locations are considered which should not contain any *replication metadata*

1. [Editable templates][page-templates]' initial nodes, as those are used as prototype when creating new pages based on the template. In case replication metadata would be included, those would end up on the newly created pages (and incorrectly expose them as being published).

Feel free to raise an issue to get the default set adjusted.

# Settings

The following options are supported apart from the default settings mentioned in [FileVault validation][2].

Option | Mandatory | Description | Default Value | Since Version
--- | --- | --- | --- | ---
`includedNodePathPatternsAndTypes` | no | Comma-separated list of node types, where each item has the format outlined below. | `.*/settings/wcm/templates/[^/]*[nt:Template], .*/settings/wcm/templates/[^/]*/structure[cq:Page], .*/settings/wcm/templates/[^/]*/policies[cq:Page], .*/settings/wcm/policies/.*[wcm/core/components/policy/policy], .*/settings/dam/cfm/models/.*[dam/cfm/models/console/components/data/entity/default], /(apps\|conf)/.*/(sling:configs\|settings/cloudconfigs)/.*[cq:Page]), /(apps\|conf)/.*/jcr:content[cq/contexthub/components/segment-page]` | 1.0.0 
`excludedNodePathPatternsAndTypes` | no | Comma-separated list of node types, where each item has the format outlined below. | `.\*/settings/wcm/templates/[^/]*/initial[cq:Page]` | 1.3.0 
`strictLastModificationDateCheck` | no | `true` means that nodes without a last modification property should always lead to validation errors. Otherwise they are handled in a lenient fashion like AEM behaves (i.e. assumption is that the modification date is -1 which is older than all replication dates). | `false` | 1.0.0
`agentNames` | no | Comma-separated list of replication/distribution agent names whose replication metadata should be checked. Only relevant for AEMaaCS where it should be set to `publish,preview` in case the [Preview tier][preview-tier] is used next to the regular publish service. | `publish` | 1.1.0

## Node type format

Each node type given in the comma-separated list given in option `includedNodePathPatternsAndTypes` or `excludedNodePathPatternsAndTypes` has the following format:

`<regex>[<jcr:primaryType or sling:resourceType>]{;<attributeName>=<attributeValue>}`. 

The given regular expression must match the node's path (fully) for the node to be checked for valid metadata. In addition the node must have the given primary type (or `sling:resourceType` in case the primary type is `nt:unstructured` or `cq:PageContent`).

Since version 1.4.0 you can additionally specify attributes per each node type.

### Node type attributes

Attribute Name | Allowed Attribute Values | Description | Since Version
--- | --- | --- | ---
`comparisonDate` | `MODIFIED` or `MODIFIED_CREATED_OR_CURRENT`. `MODIFIED` compares the replication date with either property `cq:lastModified` or `jcr:lastModified`. `MODIFIED_CREATED_OR_CURRENT` compares the replication date with property `cq:lastModified`, `jcr:lastModified` or `jcr:created` and falls back to the current date (if none of the previous properties are found). Default = `MODIFIED` | Determines the date property which should be compared with the `cq:lastReplicated` date. | 1.4.0

# Fix Violations

When the validator detects issues those can be fixed by manually adding a `cq:lastReplicationAction` and `cq:lastReplicated` properties to the according node in the underlying [DocView XML file][docview-xml] (potentially with an agent-specific suffix like `_preview`). The `cq:lastReplicationAction` property must be set to value `Activate`. The `cq:lastReplicated` property must contain a date value which is newer than the date given in either the `cq:lastModified` or `jcr:lastModified` (and for some items the `jcr:created`) property. If no modification/creation date is set then any `cq:lastReplicated` date is sufficient (is is mandatory, though). 

For example, adding 

```
cq:lastReplicated="{Date}2022-01-01T00:00:00.000+01:00"
cq:lastReplicated_preview="{Date}2022-01-01T00:00:00.000+01:00
cq:lastReplicationAction="Activate"
cq:lastReplicationAction_preview="Activate"
```

to the node which is supposed to be detected as active in the last version on both default publish and preview tiers on AEM as a Cloud Service (in case no last modification date is set). For regular AEM 6.5 environments just adding 

```
cq:lastReplicated="{Date}2022-01-01T00:00:00.000+01:00"
cq:lastReplicationAction="Activate"
```

is enough.

## Metadata Properties Location

The node where the replication and modification metadata is located differs depending on whether the affected content is inside a `cq:Page` (i.e. somewhere below its `jcr:content` node) or outside (this may be still below a `cq:Page` node but not within its `jcr:content` child). 

* For the former (inside a `cq:Page`) both the replication and modification metadata is located directly inside the top-level `jcr:content` node of the container page (this affects e.g. editable templates' structure or policy mapping nodes) 
* For the latter (outside a `cq:Page`) the replication metadata is located in a `jcr:content` node below the affected node and the modification metadata directly a property on the affected node (this affects policy nodes with resource type `wcm/core/components/policy/policy`)

## Optional Mixins

The `jcr:content` node carrying the replication metadata may have [mixin type][mixin-type] `cq:ReplicationStatus` for AEM 6.5 (usually by adding the property `jcr:mixinTypes="[cq:ReplicationStatus]"`) or `cq:ReplicationStatus2` (only available in AEMaaCS).
While the former only defines replication properties without suffix the latter only defines replication properties with suffix `_publish` and `_preview`.

Adding a mixin is optional, as the node type `nt:unstructured` or derived types like `cq:PageContent` of the `jcr:content` node do not impose any property limitations and the type is currently not used for any type based queries either.

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
          <agentNames>publish,preview</agentNames><!-- default value is publish only  -->
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

# Alternative Approaches

The [Package Replication Status Updater feature from ACS AEM Commons][package-replication-status-updater] can be used as well to maintain a correct replication status.
However this approach only works if the according packages are replicated from the Author and not directly installed through some other means. So in general this is not a viable alternative for AEM as a Cloud Service.


[aemanalyser-maven-plugin]: https://github.com/adobe/aemanalyser-maven-plugin/tree/main/aemanalyser-maven-plugin
[2]: https://jackrabbit.apache.org/filevault/validation.html
[3]: https://jackrabbit.apache.org/filevault-package-maven-plugin/index.html
[4]: https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/com/day/cq/wcm/api/reference/ReferenceProvider.html
[page-templates]: https://experienceleague.adobe.com/docs/experience-manager-65/developing/platform/templates/page-templates-editable.html
[ca-configs]: https://sling.apache.org/documentation/bundles/context-aware-configuration/context-aware-configuration.html
[aem-publish]: https://experienceleague.adobe.com/docs/experience-manager-cloud-service/content/sites/authoring/fundamentals/publishing-pages.html
[package-replication-status-updater]: https://adobe-consulting-services.github.io/acs-aem-commons/features/package-replication-status-updater/index.html
[docview-xml]: https://jackrabbit.apache.org/filevault/docview.html
[preview-tier]: https://experienceleague.adobe.com/docs/experience-manager-cloud-service/content/sites/authoring/fundamentals/previewing-content.html?lang=en
[segment-pages]: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/personalization/contexthub-segmentation
[mixin-type]: https://jackrabbit.apache.org/jcr/node-types.html#primary-vs-mixin
[content-fragment-models]: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/assets/content-fragments/content-fragments-models
