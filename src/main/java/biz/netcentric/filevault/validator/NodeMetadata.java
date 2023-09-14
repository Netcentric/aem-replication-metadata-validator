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

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;

import com.day.cq.commons.jcr.JcrConstants;

import biz.netcentric.filevault.validator.ReplicationMetadata.ReplicationActionType;

/**
 * Encapsulates path and some node metadata (last modification date and replication metadata).
 * As for replication metadata it is preferably captured in {@code jcr:content} child nodes, this object is mutable.
 */
public class NodeMetadata {

    static final Name NAME_CQ_LAST_MODIFIED = AemReplicationMetadataValidator.NAME_FACTORY.create(AemReplicationMetadataValidator.CQ_NAMESPACE_URI, "lastModified");
    private static final Name NAME_JCR_LAST_MODIFIED = AemReplicationMetadataValidator.NAME_FACTORY.create(Property.JCR_LAST_MODIFIED);

    /** this path always refers to the node supposed to contain the last modified property */
    private final String path;
    private final Map<String,ReplicationMetadata> replicationStatusPerAgent;
    private Optional<Calendar> lastModificationDate;
    /** helper variable to keep track of the nesting level below the node given by path, 0 means current node is the one supposed to contain the last modified property */
    private int currentNodeNestingLevel;

    public NodeMetadata(String path, boolean currentNodeIsParent) {
        super();
        this.path = path;
        this.replicationStatusPerAgent = new HashMap<>();
        lastModificationDate = Optional.empty();
        this.currentNodeNestingLevel = currentNodeIsParent ? -1 : 0;
    }

    public String getPath() {
        return path;
    }

    public void increaseCurrentNodeNestingLevel() {
        currentNodeNestingLevel++;
    }

    /**
     * 
     * @return {@code true} in case the node to which {@link #getPath()} refers to is reached
     */
    public boolean decreaseCurrentNodeNestingLevel() {
        return --currentNodeNestingLevel < 0;
    }

    public Optional<Calendar> getLastModificationDate() {
        return lastModificationDate;
    }

    public void captureLastModificationDate(@NotNull DocViewNode2 node, boolean strictLastModificationCheck) throws IllegalStateException, RepositoryException {
        final Calendar date;
        Optional<DocViewProperty2> property = Optional.ofNullable(node.getProperty(NAME_CQ_LAST_MODIFIED)
                .orElseGet(() -> node.getProperty(NAME_JCR_LAST_MODIFIED)
                .orElse(null)));
        if (property.isPresent()) {
            Value lastModifiedValue = AemReplicationMetadataValidator.VALUE_FACTORY.createValue(property.get().getStringValue().orElseThrow(() -> new IllegalStateException("No value found in " + property.get().getName())), PropertyType.DATE);
            date = lastModifiedValue.getDate();
        } else {
            date = Calendar.getInstance();
            // assume current date as last modified date (only for binaries and nodes with autocreated jcr:lastModified through mixin mix:lastModified)
            if (!node.getPrimaryType().orElse("").equals(JcrConstants.NT_RESOURCE) && !node.getMixinTypes().contains(JcrConstants.MIX_LAST_MODIFIED)) {
                // otherwise either ...
                if (strictLastModificationCheck) {
                    date.add(Calendar.YEAR, 1000); // ... some day in the future to make it always fail
                } else {
                    date.add(Calendar.YEAR, -1000); // ... some day in the past to make it always pass
                }
            }
        }
        lastModificationDate = Optional.of(date);
    }

    public void captureReplicationMetadata(@NotNull DocViewNode2 node, @NotNull Collection<@NotNull String> agentNames) {
        for (String agentName : agentNames) {
            replicationStatusPerAgent.put(agentName, new ReplicationMetadata(node, agentName));
        }
    }

    public Collection<ValidationMessage> validate(@NotNull ValidationMessageSeverity validationMessageSeverity, @NotNull Collection<@NotNull String> agentNames) {
        Collection<ValidationMessage> validationMessages = new LinkedList<>();
        // override nodePath as this is being called from DocumentViewXmlVallidator.validateEnd() which suffers from https://issues.apache.org/jira/browse/JCRVLT-718?
        for (String agentName : agentNames) {
            ReplicationMetadata replicationStatus = replicationStatusPerAgent.get(agentName);
            try {
                ReplicationActionType lastReplicationAction = replicationStatus.getLastReplicationAction();
                if (lastReplicationAction != ReplicationActionType.ACTIVATE) {
                    validationMessages.add(new ValidationMessage(validationMessageSeverity, "The last replication action must be 'Activate' but was '" + lastReplicationAction + "' for agent " + agentName, path, null, null, 0, 0, null));
                }
            } catch (IllegalStateException e) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "No replication action set for agent " + agentName +": " + e.getMessage(), path, null, null, 0, 0, null));
            }
            try {
                Calendar lastReplicationDate = replicationStatus.getLastPublished();
                if (!lastModificationDate.isPresent()) {
                    validationMessages.add(new ValidationMessage(validationMessageSeverity, "No last modification date captured for this path", path, null, null, 0, 0, null));
                } else {
                    // Logic from com.day.cq.wcm.core.impl.reference.converter.AssetJSONItemConverter.referenceToJSONObject()
                    if (lastReplicationDate.compareTo(lastModificationDate.get()) < 0) {
                        validationMessages.add(new ValidationMessage(validationMessageSeverity, "The replication date " + lastReplicationDate.toInstant().toString() + " for agent " + agentName
                                + " is older than the last modification date " + lastModificationDate.get().toInstant().toString(), path, null, null, 0, 0, null));
                    }
                }
            } catch (IllegalStateException e) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "No replication date set for agent " + agentName +": " + e.getMessage(), path, null, null, 0, 0, null));
            }
        }
        return validationMessages;
    }
    
}
