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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private static final @NotNull Set<@NotNull String> DEFAULT_AGENT_NAMES = Collections.singleton(ReplicationMetadata.DEFAULT_AGENT_NAME);
    private static final @NotNull Map<Pattern, String> DEFAULT_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES = createDefaultIncludeMap();

    private static Map<Pattern, String> createDefaultIncludeMap() {
        Map<Pattern, String> map = new HashMap<>();
        // by default: editable templates (as found by com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider)
        map.put(Pattern.compile(".*/settings/wcm/templates/[^/]*/structure"), NameConstants.NT_PAGE);
        // content policies mappings (as found by com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider)
        map.put(Pattern.compile(".*/settings/wcm/templates/[^/]*/policies"), NameConstants.NT_PAGE);
        // mapped content policies (as found by com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider)
        map.put(Pattern.compile(".*/settings/wcm/policies/.*"), RESOURCE_TYPE_CONTENT_POLICY);
        // and regular context-aware configuration (as found by https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java)
        map.put(Pattern.compile("/(apps|conf)/.*/(sling:configs|settings/cloudconfigs)/.*"), NameConstants.NT_PAGE);
        return Collections.unmodifiableMap(map);
    }

    private static final @NotNull Map<Pattern, String> DEFAULT_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES = createDefaultExcludeMap();

    private static Map<Pattern, String> createDefaultExcludeMap() {
        Map<Pattern, String> map = new HashMap<>();
        map.put(Pattern.compile(".*/settings/wcm/templates/[^/]*/initial"), NameConstants.NT_PAGE);
        return Collections.unmodifiableMap(map);
    }
    
    @Nullable
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final @NotNull Map<Pattern, String> includedNodePathsPatternsAndTypes;
        if (settings.getOptions().containsKey(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES)) {
            includedNodePathsPatternsAndTypes = parseNodePathPatternsAndTypes(settings.getOptions().get(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES));
        } else {
            includedNodePathsPatternsAndTypes = DEFAULT_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES;
        }
        final @NotNull Map<Pattern, String> excludedNodePathsPatternsAndTypes;
        if (settings.getOptions().containsKey(OPTION_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES)) {
            excludedNodePathsPatternsAndTypes = parseNodePathPatternsAndTypes(settings.getOptions().get(OPTION_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES));
        } else {
            excludedNodePathsPatternsAndTypes = DEFAULT_EXCLUDED_NODE_PATH_PATTERNS_AND_TYPES;
        }
        boolean strictLastModificationDateCheck = Boolean.parseBoolean(settings.getOptions().get(OPTION_STRICT_LAST_MODIFICATION_CHECK));
        final @NotNull Set<@NotNull String> agentNames;
        if (settings.getOptions().containsKey(OPTION_AGENT_NAMES)) {
            agentNames = Arrays.stream(settings.getOptions().get(OPTION_AGENT_NAMES).split(",")).map(String::trim).collect(Collectors.toSet());
        } else {
            agentNames = DEFAULT_AGENT_NAMES;
        }
        return new AemReplicationMetadataValidator(settings.getDefaultSeverity(), includedNodePathsPatternsAndTypes, excludedNodePathsPatternsAndTypes, strictLastModificationDateCheck, agentNames);
    }

    static @NotNull Map<Pattern, String> parseNodePathPatternsAndTypes(String option) {
        return Pattern.compile(",").splitAsStream(option)
                .map((entry) -> {
                    int startType = entry.lastIndexOf('[');
                    if (startType == -1) {
                        throw new IllegalArgumentException("Each entry must end with a type enclosed by \"[\" and \"]\", but found entry " + entry);
                    }
                    if (entry.charAt(entry.length() - 1) != ']') {
                        throw new IllegalArgumentException("Each entry must end  with \"]\", but found entry " + entry);
                    }
                    Pattern pattern = Pattern.compile(entry.substring(0, startType));
                    String type = entry.substring(startType + 1, entry.length()-1);
                    return new AbstractMap.SimpleEntry<>(pattern, type);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
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
