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
import java.util.HashMap;
import java.util.Map;
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

    // comma-separated list of items in the format "<regex>[<primary-type>]"
    private static final String OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES = "includedNodePathPatternsAndTypes";
    private static final String OPTION_STRICT_LAST_MODIFICATION_CHECK = "strictLastModificationDateCheck";

    private static final Map<Pattern, String> DEFAULT_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES = createDefaultMap();

    private static Map<Pattern, String> createDefaultMap() {
        Map<Pattern, String> map = new HashMap<>();
        // by default: editable templates (as found by com.day.cq.wcm.core.impl.reference.PageTemplateReferenceProvider)
        map.put(Pattern.compile(".*/settings/wcm/templates/[^/]*/structure"), NameConstants.NT_PAGE);
        // content policies (as found by com.day.cq.wcm.core.impl.reference.ContentPolicyReferenceProvider)
        map.put(Pattern.compile(".*/settings/wcm/templates/[^/]*/policies"), NameConstants.NT_PAGE);
        // and regular context-aware configuration (as found by https://github.com/adobe/aem-core-wcm-components/blob/main/bundles/core/src/main/java/com/adobe/cq/wcm/core/components/internal/services/CaConfigReferenceProvider.java)
        map.put(Pattern.compile("/(apps|conf)/.*/(sling:configs|settings/cloudconfigs)/.*"), NameConstants.NT_PAGE);
        return map;
    }

    @Nullable
    public Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {
        final Map<Pattern, String> includedNodePathsPatternsAndTypes;
        if (settings.getOptions().containsKey(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES)) {
            includedNodePathsPatternsAndTypes = parseNodePathPatternsAndTypes(settings.getOptions().get(OPTION_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES));
        } else {
            includedNodePathsPatternsAndTypes = DEFAULT_INCLUDED_NODE_PATH_PATTERNS_AND_TYPES;
        }
        boolean strictLastModificationDateCheck = Boolean.parseBoolean(settings.getOptions().get(OPTION_STRICT_LAST_MODIFICATION_CHECK));
        return new AemReplicationMetadataValidator(settings.getDefaultSeverity(), includedNodePathsPatternsAndTypes, strictLastModificationDateCheck);
    }

    static Map<Pattern, String> parseNodePathPatternsAndTypes(String option) {
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
