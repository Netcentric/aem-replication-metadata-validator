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

import java.text.ChoiceFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.jetbrains.annotations.NotNull;

/**
 * Replication metadata from {@link DocViewNode2}s.
 * The properties are accessed lazily.
 * Metadata always refer to a specific agent (which may be publish=default)
 *
 */
public class ReplicationMetadata {

    static final String DEFAULT_AGENT_NAME = "publish";
    private static final String CQ_LAST_REPLICATED = "lastReplicated";
    private static final String CQ_LAST_PUBLISHED = "lastPublished";
    private static final String CQ_LAST_REPLICATION_ACTION = "lastReplicationAction";

    private final @NotNull DocViewNode2 node;
    private final @NotNull String agentName;

    public ReplicationMetadata(@NotNull DocViewNode2 node, @NotNull String agentName) {
        super();
        this.node = node;
        this.agentName = agentName;
    }

    static DocViewProperty2 getProperty(@NotNull DocViewNode2 node, @NotNull String agentName, boolean allowNullReturnValue, @NotNull String namespaceUri, String... propertyNames) {
        // only use agent specific metadata if name is not "publish", replicating logic from com.day.cq.wcm.core.impl.reference.ReferenceReplicationStatusProvider.initReplicationStatusMap() line 67
        String metadataPropertySuffix = agentName.equals(DEFAULT_AGENT_NAME) ? "" : ("_" + agentName);
        List<String> suffixedPropertyNames = Arrays.stream(propertyNames).map(s -> s + metadataPropertySuffix).collect(Collectors.toList());
        for (String propertyName : suffixedPropertyNames) {
            Optional<DocViewProperty2> property = node.getProperty(AemReplicationMetadataValidator.NAME_FACTORY.create(namespaceUri, propertyName));
            if (property.isPresent()) {
                return property.get();
            }
        }
        if (!allowNullReturnValue) {
            ChoiceFormat replicationProperties = new ChoiceFormat(
                    "1#Replication property|1.0<Replication properties");
            throw new IllegalStateException(replicationProperties.format(suffixedPropertyNames.size()) + " " + String.join(" or ", suffixedPropertyNames.stream().map(s -> "{" + namespaceUri + "}" + s).collect(Collectors.toList())) + " not found");
        }
        return null;
    }

    public Calendar getLastReplicationDate(boolean allowNullReturnValue) {
        // this logic is derived from com.day.cq.replication.impl.ReplicationStatusImpl.readAgentStatus(...)
        // and com.day.cq.wcm.core.impl.reference.ReferenceReplicationStatusProvider.initReplicationStatusMap(...)
        DocViewProperty2 property = getProperty(node, agentName, allowNullReturnValue, AemReplicationMetadataValidator.CQ_NAMESPACE_URI, CQ_LAST_REPLICATED, CQ_LAST_PUBLISHED);
        Optional<Calendar> lastReplicationDate = Optional.ofNullable(property)
                .flatMap(DocViewProperty2::getStringValue)
                .map(AemReplicationMetadataValidator.VALUE_FACTORY::createValue)
                .map(t -> {
            try {
                return t.getDate();
            } catch (RepositoryException e) {
                throw new IllegalStateException(e);
            }
        });
        if (!allowNullReturnValue && !lastReplicationDate.isPresent()) {
            throw new IllegalStateException("No replication property found with name  " + property.getName());
        }
        return lastReplicationDate.orElse(null);
    }

    public ReplicationActionType getLastReplicationAction(boolean allowNullReturnValue) {
     // this logic is derived from com.day.cq.replication.impl.ReplicationStatusImpl.readAgentStatus(...)
        // and com.day.cq.wcm.core.impl.reference.ReferenceReplicationStatusProvider.initReplicationStatusMap(...)
        DocViewProperty2 property = getProperty(node, agentName, allowNullReturnValue, AemReplicationMetadataValidator.CQ_NAMESPACE_URI, CQ_LAST_REPLICATION_ACTION);
        Optional<ReplicationActionType> replicationActionType = Optional.ofNullable(property)
                .flatMap(DocViewProperty2::getStringValue)
                .map(ReplicationActionType::fromName);
        if (!allowNullReturnValue && !replicationActionType.isPresent()) {
            throw new IllegalStateException("No replication property found with name  " + property.getName());
        }
        return replicationActionType.orElse(null);
    }

    /**
     * Copy from {@code com.day.cq.replication.ReplicationActionType} from {@code uber-jar:6.5.17}
     */
    public enum ReplicationActionType {
        ACTIVATE("Activate"), DEACTIVATE("Deactivate"), DELETE("Delete"), TEST("Test"),

        @Deprecated
        REVERSE("Reverse"), INTERNAL_POLL("Internal Poll");

        private final String name;

        private ReplicationActionType(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static ReplicationActionType fromName(String n) {
            if (n == null) {
                return null;
            } else {
                try {
                    return valueOf(n.toUpperCase());
                } catch (IllegalArgumentException var2) {
                    return null;
                }
            }
        }
    }
}
