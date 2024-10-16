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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;

import biz.netcentric.filevault.validator.ReplicationMetadata.ReplicationActionType;

/**
 * Encapsulates path and some node metadata (last modification date and replication metadata).
 * As for replication metadata it is preferably captured in {@code jcr:content} child nodes, this object is mutable.
 */
public class NodeMetadata {

    /*
     * If {@code true} the node is supposed to contain replication metadata which indicates it is active and not modified,
     *  otherwise it should not contain any replication metadata at all
     */
    final boolean isExcluded;
    /** this path always refers to the node supposed to contain the last modified property */
    private final String path;
    private final Map<String,ReplicationMetadata> replicationStatusPerAgent;
    private final DateProperty comparisonDateProperty;
    private Optional<Map.Entry<Calendar, String>> comparisonDateAndLabel;
    /** helper variable to keep track of the nesting level below the node given by path, 0 means current node is the one supposed to contain the last modified property */
    private int currentNodeNestingLevel;

    public NodeMetadata(boolean isExcluded, String path, boolean currentNodeIsParent, DateProperty comparisonDateProperty) {
        super();
        this.isExcluded = isExcluded;
        this.path = path;
        this.replicationStatusPerAgent = new HashMap<>();
        comparisonDateAndLabel = Optional.empty();
        this.currentNodeNestingLevel = currentNodeIsParent ? -1 : 0;
        this.comparisonDateProperty = comparisonDateProperty;
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

    public Optional<Map.Entry<Calendar, String>> getComparisonDateAndLabel() {
        return comparisonDateAndLabel;
    }

    public void captureComparisonDate(@NotNull DocViewNode2 node) throws IllegalStateException, RepositoryException {
        comparisonDateAndLabel = comparisonDateProperty.extractDate(node);
    }

    /**
     * This method never fails until actually dereferencing data in another method.
     * @param node the node to capture replication metadata from
     * @param agentNames the agent names to capture replication metadata for
     */
    public void captureReplicationMetadata(@NotNull DocViewNode2 node, @NotNull Collection<@NotNull String> agentNames) {
        for (String agentName : agentNames) {
            replicationStatusPerAgent.put(agentName, new ReplicationMetadata(node, agentName));
        }
    }

    public Collection<ValidationMessage> validate(@NotNull ValidationMessageSeverity validationMessageSeverity, @NotNull Collection<@NotNull String> agentNames, boolean strictLastModificationCheck) {
        Collection<ValidationMessage> validationMessages = new LinkedList<>();
        // override nodePath as this is being called from DocumentViewXmlValidator.validateEnd() which suffers from https://issues.apache.org/jira/browse/JCRVLT-718?
        for (String agentName : agentNames) {
            if (isExcluded) {
                validateNoReplicationMetadata(validationMessageSeverity, validationMessages, agentName);
            } else {
                validateIsPublished(validationMessageSeverity, validationMessages, agentName, strictLastModificationCheck);
            }
        }
        return validationMessages;
    }

    private void validateNoReplicationMetadata(@NotNull ValidationMessageSeverity validationMessageSeverity,
            Collection<ValidationMessage> validationMessages, String agentName) {
        ReplicationMetadata replicationStatus = replicationStatusPerAgent.get(agentName);
        ReplicationActionType lastReplicationAction = replicationStatus.getLastReplicationAction(true);
        if (lastReplicationAction != null) {
            validationMessages.add(new ValidationMessage(validationMessageSeverity, "Last replication action not allowed for this path but is " + lastReplicationAction, path, null, null, 0, 0, null));
        }
        Calendar lastReplicationDate = replicationStatus.getLastReplicationDate(true);
        if (lastReplicationDate != null) {
            validationMessages.add(new ValidationMessage(validationMessageSeverity, "Last replication date not allowed for this path but is " + lastReplicationDate.toInstant().toString(), path, null, null, 0, 0, null));
        }
    }

    private void validateIsPublished(ValidationMessageSeverity validationMessageSeverity, Collection<ValidationMessage> validationMessages, String agentName, boolean strictLastModificationCheck) {
        ReplicationMetadata replicationStatus = replicationStatusPerAgent.get(agentName);
        if (replicationStatus == null) {
            if (path.endsWith("/" + NameConstants.QUALIFIED_NAME_JCR_CONTENT)) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "No jcr:content node found at all where replication data would have been captured for agent " + agentName, path, null, null, 0, 0, null));
            } else {
                // this is a programming error most probably
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "Replication status not yet populated via captureReplicationMetadata() for agent " + agentName, path, null, null, 0, 0, null));
            }
            replicationStatus = ReplicationMetadata.EMPTY;
        }
        try {
            ReplicationActionType lastReplicationAction = replicationStatus.getLastReplicationAction(false);
            if (lastReplicationAction != ReplicationActionType.ACTIVATE) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "The last replication action must be 'Activate' but was '" + lastReplicationAction + "' for agent " + agentName, path, null, null, 0, 0, null));
            }
        } catch (IllegalStateException e) {
            validationMessages.add(new ValidationMessage(validationMessageSeverity, "No replication action set for agent " + agentName +": " + e.getMessage(), path, null, null, 0, 0, null));
        }
        final Calendar lastReplicationDate;
        try {
            lastReplicationDate = replicationStatus.getLastReplicationDate(false);
            // unfortunately this is not allowed to be null (always dereferenced in com.adobe.cq.xf.impl.servlet.ExperienceFragmentsReferencesServlet.writeJsonForReference)
        } catch (IllegalStateException e) {
            validationMessages.add(new ValidationMessage(validationMessageSeverity, "No replication date set for agent " + agentName +": " + e.getMessage(), path, null, null, 0, 0, null));
            return;
        }
        if (!comparisonDateAndLabel.isPresent()) {
            if (strictLastModificationCheck) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "No last modification property set and don't fall back to -1 due to strict check option", path, null, null, 0, 0, null));
            } else {
                if (lastReplicationDate.getTimeInMillis() < 0L) {
                        validationMessages.add(new ValidationMessage(validationMessageSeverity, "The replication date " + lastReplicationDate.toInstant().toString() + " for agent " + agentName
                                + " is older than the implicit last modification date 0", path, null, null, 0, 0, null));
                }
            }
        } else {
            // Logic from com.day.cq.wcm.core.impl.reference.converter.AssetJSONItemConverter.referenceToJSONObject()
            Calendar comparisonDate = comparisonDateAndLabel.get().getKey();
            String comparisonDateLabel = comparisonDateAndLabel.get().getValue();
            if (lastReplicationDate.compareTo(comparisonDate) < 0) {
                validationMessages.add(new ValidationMessage(validationMessageSeverity, "The replication date " + lastReplicationDate.toInstant().toString() + " for agent " + agentName
                        + " is older than the comparison date " + comparisonDate.toInstant().toString() + " (" + comparisonDateLabel + ")" , path, null, null, 0, 0, null));
            }
        }
    }
    
}
