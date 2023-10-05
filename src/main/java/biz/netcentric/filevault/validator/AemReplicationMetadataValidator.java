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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.NameConstants;

public class AemReplicationMetadataValidator implements DocumentViewXmlValidator {

    static final NameFactory NAME_FACTORY = NameFactoryImpl.getInstance();
    static final ValueFactory VALUE_FACTORY = ValueFactoryImpl.getInstance();
    static final String CQ_NAMESPACE_URI = "http://www.day.com/jcr/cq/1.0"; // no constant defined in https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/constant-values.html

    private static final Name NAME_JCR_CONTENT = org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_CONTENT;
    private static final Name NAME_SLING_RESOURCETYPE = NAME_FACTORY.create(JcrResourceConstants.SLING_NAMESPACE_URI, SlingConstants.PROPERTY_RESOURCE_TYPE);

    private static final Logger LOGGER = LoggerFactory.getLogger(AemReplicationMetadataValidator.class);

    private final @NotNull ValidationMessageSeverity validationMessageSeverity;
    private final @NotNull Map<Pattern, String> includedNodePathsPatternsAndTypes;
    private final @NotNull Map<Pattern, String> excludedNodePathsPatternsAndTypes;
    private final boolean strictLastModificationCheck;
    private final @NotNull Set<@NotNull String> agentNames;
    private Queue<NodeMetadata> relevantNodeMetadata = Collections.asLifoQueue(new ArrayDeque<>());

    public AemReplicationMetadataValidator(@NotNull ValidationMessageSeverity validationMessageSeverity, @NotNull Map<Pattern, String> includedNodePathsPatternsAndTypes,
            @NotNull Map<Pattern, String> excludedNodePathsPatternsAndTypes, boolean strictLastModificationDateCheck, @NotNull Set<@NotNull String> agentNames) {
        this.validationMessageSeverity = validationMessageSeverity;
        this.includedNodePathsPatternsAndTypes = includedNodePathsPatternsAndTypes;
        this.excludedNodePathsPatternsAndTypes = excludedNodePathsPatternsAndTypes;
        this.strictLastModificationCheck = strictLastModificationDateCheck;
        this.agentNames = agentNames;
    }

    @Nullable
    public Collection<ValidationMessage> done() {
        return null;
    }

    /**
     * Returns the node metadata this node path refers to (might be belonging to the parent, in case this has name "jcr:content")
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
        Optional<Entry<Pattern, String>> entry = includedNodePathsPatternsAndTypes.entrySet().stream()
                .filter(e -> e.getKey().matcher(nodePath).matches())
                .findFirst();
        boolean isExclude = false;
        if (!entry.isPresent()) {
            entry = excludedNodePathsPatternsAndTypes.entrySet().stream()
                    .filter(e -> e.getKey().matcher(nodePath).matches())
                    .findFirst();
            if (!entry.isPresent()) {
                return Optional.empty();
            } else {
                LOGGER.debug("Potential excludedNodePathPatternAndType {}", entry.get());
                isExclude = true;
            }
        } else {
            LOGGER.debug("Potential includedNodePathPatternAndType {}", entry.get());
        }
        boolean isNodeRelevant = isRelevantNodeType(node, entry.get().getValue());
        if (!isNodeRelevant) {
            return Optional.empty();
        } else {
            if (NameConstants.NT_PAGE.equals(entry.get().getValue())) {
                LOGGER.debug("Waiting for jcr:content below {}", nodePath);
                currentMetadata = new NodeMetadata(isExclude, nodePath + "/" + NameConstants.NN_CONTENT, true);
                relevantNodeMetadata.add(currentMetadata);
                return Optional.empty();
            } else {
                currentMetadata = new NodeMetadata(isExclude, nodePath, false);
                relevantNodeMetadata.add(currentMetadata);
                return Optional.of(currentMetadata);
            }
        }
    }

    private static boolean isRelevantNodeType(DocViewNode2 node, String type) {
        boolean isNodeRelevant = node.getPrimaryType().equals(Optional.of(type));
        if (!isNodeRelevant) {
            // if node type == nt:unstructured, evaluate sling:resourceType instead
            if (node.getPrimaryType().equals(Optional.of(JcrConstants.NT_UNSTRUCTURED))) {
                isNodeRelevant = node.getPropertyValue(NAME_SLING_RESOURCETYPE).equals(Optional.of(type));
            }
        }
        return isNodeRelevant;
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
                currentMetadata.captureLastModificationDate(node);
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
