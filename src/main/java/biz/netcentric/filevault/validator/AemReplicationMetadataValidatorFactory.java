/*-
 * #%L
 * AEM Replication Metadata Validator
 * %%
 * Copyright (C) 2023 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */
package biz.netcentric.filevault.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.MetaInfServices;

import com.day.cq.wcm.api.NameConstants;

@MetaInfServices
public class AemReplicationMetadataValidatorFactory implements ValidatorFactory {

    private static final String RESOURCE_TYPE_CONTENT_POLICY = "wcm/core/components/policy/policy";
    // comma-separated list of items in the format "<regex>[<primary-type>]"
    private static final String OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES = "includedNodePathPatternsAndTypes";
    // comma-separated list of items in the format "<regex>[<primary-type>]"
    private static final String OPTION_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES = "excludedNodePathPatternsAndTypes";
    private static final String OPTION_STRICT_LAST_MODIFICATION_CHECK = "strictLastModificationDateCheck";
    private static final String OPTION_AGENT_NAMES = "agentNames";
    private static final String ATTRIBUTE_COMPARISON_DATE = "comparisonDate";
    private static final @NotNull Set<@NotNull String> DEFAULT_AGENT_NAMES = Collections.singleton(ReplicationMetadata.DEFAULT_AGENT_NAME);
    private static final @NotNull Collection<TypeSettings> DEFAULT_INCLUDED_TYPES_SETTINGS = createDefaultIncludedTypesSettings();
    private static final String RESOURCE_TYPE_SEGMENT_PAGE = "cq/contexthub/components/segment-page";
    private static final String RESOURCE_TYPE_CONTENT_FRAGMENT_MODEL_PAGE = "dam/cfm/models/console/components/data/entity/default";

    private static Collection<TypeSettings> createDefaultIncludedTypesSettings() {
        Collection<TypeSettings> typesSettings = new ArrayList<>();
        // by default: editable templates and their structure child (as found by com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider)
        typesSettings.add(new TypeSettings(".*/settings/wcm/templates/[^/]*", NameConstants.NT_TEMPLATE));
        typesSettings.add(new TypeSettings(".*/settings/wcm/templates/[^/]*/structure", NameConstants.NT_PAGE));
        // content policies mappings (as found by com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider)
        typesSettings.add(new TypeSettings(".*/settings/wcm/templates/[^/]*/policies", NameConstants.NT_PAGE));
        // mapped content policies (as found by com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider)
        typesSettings.add(new TypeSettings(".*/settings/wcm/policies/.*", RESOURCE_TYPE_CONTENT_POLICY));
        // content fragment models (as found by com.adobe.cq.dam.cfm.impl.search.ContentFragmentReferencePublishProvider)
        TypeSettings typeSettings = new TypeSettings(".*/settings/dam/cfm/models/.*", RESOURCE_TYPE_CONTENT_FRAGMENT_MODEL_PAGE);
        typeSettings.setComparisonDatePropery(DateProperty.MODIFIED_CREATED_OR_CURRENT);
        typesSettings.add(typeSettings);
        // regular context-aware configuration (as found by https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java)
        typesSettings.add(new TypeSettings("/(apps|conf)/.*/(sling:configs|settings/cloudconfigs)/.*", NameConstants.NT_PAGE));
        // segment pages (as found by com.day.cq.personalization.impl.TargetedComponentReferenceProvider)
        typesSettings.add(new TypeSettings("/(apps|conf)/.*/jcr:content", RESOURCE_TYPE_SEGMENT_PAGE));
        return Collections.unmodifiableCollection(typesSettings);
    }

    private static final @NotNull  Collection<TypeSettings> DEFAULT_EXCLUDED_TYPES_SETTINGS = createDefaultExcludedTypesSettings();

    private static Collection<TypeSettings> createDefaultExcludedTypesSettings() {
        Collection<TypeSettings> typesSettings = new ArrayList<>();
        typesSettings.add(new TypeSettings(".*/settings/wcm/templates/[^/]*/initial", NameConstants.NT_PAGE));
        return Collections.unmodifiableCollection(typesSettings);
    }
    
    @Nullable
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final @NotNull Collection<TypeSettings> includedTypesSettings;
        if (settings.getOptions().containsKey(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES)) {
            includedTypesSettings = parseTypesSettings(settings.getOptions().get(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES));
        } else {
            includedTypesSettings = DEFAULT_INCLUDED_TYPES_SETTINGS;
        }
        final @NotNull Collection<TypeSettings> excludedTypesSettings;
        if (settings.getOptions().containsKey(OPTION_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES)) {
            excludedTypesSettings = parseTypesSettings(settings.getOptions().get(OPTION_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES));
        } else {
            excludedTypesSettings = DEFAULT_EXCLUDED_TYPES_SETTINGS;
        }
        boolean strictLastModificationDateCheck = Boolean.parseBoolean(settings.getOptions().get(OPTION_STRICT_LAST_MODIFICATION_CHECK));
        final @NotNull Set<@NotNull String> agentNames;
        if (settings.getOptions().containsKey(OPTION_AGENT_NAMES)) {
            agentNames = Arrays.stream(settings.getOptions().get(OPTION_AGENT_NAMES).split(",")).map(String::trim).collect(Collectors.toSet());
        } else {
            agentNames = DEFAULT_AGENT_NAMES;
        }
        return new AemReplicationMetadataValidator(settings.getDefaultSeverity(), includedTypesSettings, excludedTypesSettings, strictLastModificationDateCheck, agentNames);
    }

    static @NotNull Collection<TypeSettings> parseTypesSettings(String option) {
        return Pattern.compile("\\s*,\\s*").splitAsStream(option)
                .map(AemReplicationMetadataValidatorFactory::parseTypeSettings)
                .collect(Collectors.toList());
    }

    static @NotNull TypeSettings parseTypeSettings(String entry) {
        int startType = entry.lastIndexOf('[');
        int endType = entry.indexOf(']', startType);
        if (startType == -1 || endType == -1) {
            throw new IllegalArgumentException("Each entry must have a type enclosed by \"[\" and \"]\", but found entry " + entry);
        }
        String pattern = entry.substring(0, startType);
        String type = entry.substring(startType + 1, endType);
        TypeSettings typeSettings = new TypeSettings(pattern, type);
        if (endType < entry.length()-1) {
            // type may have additional attributes separated by ";"
            if (entry.charAt(endType+1) != ';') {
                throw new IllegalArgumentException("Each entry may either end with the type enclosed by \"[\" and \"]\" or some attributes separated by \";\"");
            }
            String attributes = entry.substring(endType+2);
            parseAttributes(typeSettings, attributes);
        }
        return typeSettings;
    }

    static void parseAttributes(TypeSettings typeSettings, String attributes) {
        for (String attribute : attributes.split(";")) {
            String[] attributePair = attribute.split("=", 2);
            if (attributePair.length != 2) {
                throw new IllegalArgumentException("Each attribute must have the format \"name=value\" but found " + attribute);
            }
            String attributeName = attributePair[0];
            String attributeValue = attributePair[1];
            if (attributeName.equals(ATTRIBUTE_COMPARISON_DATE)) {
                typeSettings.setComparisonDatePropery(DateProperty.valueOf(attributeValue));
            } else {
                throw new IllegalArgumentException("Unsupported attribute with name " + attributeName);
            }
        }
    }
    public boolean shouldValidateSubpackages() {
        return true;
    }

    @NotNull
    public String getId() {
        return "netcentric-aem-replication-metadata";
    }

    public int getServiceRanking() {
        return 0;
    }

}
