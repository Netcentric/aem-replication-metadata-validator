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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.wcm.api.NameConstants;

public class AemReplicationMetadataValidator implements DocumentViewXmlValidator {

    private static final Name NAME_JCR_CONTENT = org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_CONTENT;
    private static final Logger LOGGER = LoggerFactory.getLogger(AemReplicationMetadataValidator.class);

    private final @NotNull ValidationMessageSeverity validationMessageSeverity;
    private final @NotNull Collection<TypeSettings> includedTypesSettings;
    private final @NotNull Collection<TypeSettings> excludedTypesSettings;
    private final boolean strictLastModificationCheck;
    private final @NotNull Set<@NotNull String> agentNames;
    private Queue<NodeMetadata> relevantNodeMetadata = Collections.asLifoQueue(new ArrayDeque<>());

    public AemReplicationMetadataValidator(@NotNull ValidationMessageSeverity validationMessageSeverity, @NotNull Collection<TypeSettings> includedTypesSettings,
            @NotNull Collection<TypeSettings> excludedTypesSettings, boolean strictLastModificationDateCheck, @NotNull Set<@NotNull String> agentNames) {
        this.validationMessageSeverity = validationMessageSeverity;
        this.includedTypesSettings = includedTypesSettings;
        this.excludedTypesSettings = excludedTypesSettings;
        this.strictLastModificationCheck = strictLastModificationDateCheck;
        this.agentNames = agentNames;
    }

    @Nullable
    public Collection<ValidationMessage> done() {
        return null;
    }

    /**
     * Returns the node metadata this node path refers to (might be belonging to the parent, in case this node has name "jcr:content")
     * @param nodePath
     * @param node
     * @return the node metadata or empty if not relevant
     */
    private Optional<NodeMetadata> getNodeMetadata(@NotNull String nodePath, @NotNull DocViewNode2 node) {
        NodeMetadata currentMetadata = relevantNodeMetadata.peek();
        if (currentMetadata != null && (
                nodePath.equals(currentMetadata.getPath()) ||
                nodePath.equals(currentMetadata.getPath()  + "/" + NameConstants.NN_CONTENT))) {
            return Optional.of(currentMetadata);
        }
        // first check includes, then excludes, first match returning relevant metadata wins
        boolean isExclude = false;
        Optional<NodeMetadata> newMetadata = includedTypesSettings.stream()
                .map(e -> getNodeMetadata(false, nodePath, node, e))
                .filter(Optional::isPresent) // only interested in first result returning new metadata
                .map(Optional::get)
                .findFirst();
        if (!newMetadata.isPresent()) {
            newMetadata = excludedTypesSettings.stream()
                    .map(e -> getNodeMetadata(true, nodePath, node, e))
                    .filter(Optional::isPresent) // only interested in first result returning new metadata
                    .map(Optional::get)
                    .findFirst();
            isExclude = true;
        }
        if (newMetadata.isPresent()) {
            LOGGER.debug("Potential {} {}", isExclude ? "excludedNodePathPatternAndType" : "includedNodePathPatternAndType", newMetadata.get());
        }
        return newMetadata;
        
    }

    private Optional<NodeMetadata> getNodeMetadata(boolean isExclude, @NotNull String nodePath, @NotNull DocViewNode2 node, @NotNull TypeSettings typeSettings) {
        if (!typeSettings.matches(nodePath, node)) {
            return Optional.empty();
        } else {
            String actualPrimaryType = node.getPrimaryType().orElse("");
            NodeMetadata currentMetadata;
            if (NameConstants.NT_PAGE.equals(actualPrimaryType) || NameConstants.NT_TEMPLATE.equals(actualPrimaryType)) {
                LOGGER.debug("Waiting for jcr:content below {}", nodePath);
                currentMetadata = new NodeMetadata(isExclude, nodePath + "/" + NameConstants.NN_CONTENT, true, typeSettings.getComparisonDate());
                relevantNodeMetadata.add(currentMetadata);
                return Optional.empty();
            } else {
                currentMetadata = new NodeMetadata(isExclude, nodePath, false, typeSettings.getComparisonDate());
                relevantNodeMetadata.add(currentMetadata);
                return Optional.of(currentMetadata);
            }
        }
    }

    @Override
    @Nullable
    public Collection<ValidationMessage> validate(@NotNull DocViewNode2 node, @NotNull NodeContext nodeContext, boolean isRoot) {
        // increase node depth level of all current metadata
        relevantNodeMetadata.forEach(NodeMetadata::increaseCurrentNodeNestingLevel);

        Optional<NodeMetadata> optionalCurrentMetadata = getNodeMetadata(nodeContext.getNodePath(), node);

        // skipping irrelevant nodes
        if (!optionalCurrentMetadata.isPresent()) {
            return null;
        }
        NodeMetadata currentMetadata = optionalCurrentMetadata.get();
        if (currentMetadata.getPath().equals(nodeContext.getNodePath())) {
            try {
                currentMetadata.captureComparisonDate(node);
            } catch (IllegalStateException|RepositoryException e) {
                return Collections.singletonList(new ValidationMessage(validationMessageSeverity, "Invalid last modification date found", e));
            }
            currentMetadata.captureReplicationMetadata(node, agentNames);
        } else if (node.getName().equals(NAME_JCR_CONTENT)) {
            // capture replication metadata in jcr:content child node
            String parentNodePath = Text.getRelativeParent(nodeContext.getNodePath(), 1);
            if (currentMetadata.getPath().equals(parentNodePath)) {
                currentMetadata.captureReplicationMetadata(node, agentNames);
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Collection<ValidationMessage> validateEnd(@NotNull DocViewNode2 node, @NotNull NodeContext nodeContext, boolean isRoot) {
        // Due to https://issues.apache.org/jira/browse/JCRVLT-718? one cannot rely on nodeContext.getNodePath()
        // therefore rely on NodeMetadata.currentNodeNestingLevel instead
        
        Iterator<NodeMetadata> iterator = relevantNodeMetadata.iterator();
        while (iterator.hasNext()) {
            NodeMetadata currentMetadata = iterator.next();
            if (currentMetadata.decreaseCurrentNodeNestingLevel()) {
                LOGGER.debug("End waiting for jcr:content below {}", currentMetadata.getPath());
                iterator.remove();
                return currentMetadata.validate(validationMessageSeverity, agentNames, strictLastModificationCheck);
            }
        }
        return null;
    }

}
