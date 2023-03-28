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
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.NameConstants;

public class AemReplicationMetadataValidator implements DocumentViewXmlValidator {

    static final String CQ_NAMESPACE_URI = "http://www.day.com/jcr/cq/1.0"; // no constant defined in https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/constant-values.html
    private static final Name NAME_CQ_LAST_REPLICATED = NameFactoryImpl.getInstance().create(CQ_NAMESPACE_URI, "lastReplicated");
    private static final Name NAME_CQ_LAST_PUBLISHED = NameFactoryImpl.getInstance().create(CQ_NAMESPACE_URI, "lastPublished");
    private static final Name NAME_CQ_LAST_MODIFIED =  NameFactoryImpl.getInstance().create(CQ_NAMESPACE_URI, "lastModified");
    private static final Name NAME_JCR_LAST_MODIFIED = NameFactoryImpl.getInstance().create(Property.JCR_LAST_MODIFIED);

    private static final Logger LOGGER = LoggerFactory.getLogger(AemReplicationMetadataValidator.class);

    private final ValueFactory valueFactory;
    private final @NotNull ValidationMessageSeverity validationMessageSeverity;
    private final Map<Pattern, String> includedNodePathsPatternsAndTypes;
    private final boolean strictLastModificationCheck;
    private Queue<String> relevantPagePaths = Collections.asLifoQueue(new ArrayDeque<>());

    public AemReplicationMetadataValidator(@NotNull ValidationMessageSeverity validationMessageSeverity, Map<Pattern, String> includedNodePathsPatternsAndTypes, boolean strictLastModificationDateCheck) {
        this.valueFactory = ValueFactoryImpl.getInstance();
        this.validationMessageSeverity = validationMessageSeverity;
        this.includedNodePathsPatternsAndTypes = includedNodePathsPatternsAndTypes;
        this.strictLastModificationCheck = strictLastModificationDateCheck;
    }

    @Nullable
    public Collection<ValidationMessage> done() {
        return null;
    }

    public enum ValidationType {
        NONE,
        DIRECT,
        JCR_CONTENT_CHILD
    }

    private boolean isNodeRelevant(@NotNull String nodePath, @NotNull DocViewNode2 node) {
        if (nodePath.equals(relevantPagePaths.peek() + "/" + NameConstants.NN_CONTENT)) {
            return true;
        }
        Optional<Entry<Pattern, String>> entry = includedNodePathsPatternsAndTypes.entrySet().stream()
                .filter(e -> e.getKey().matcher(nodePath).matches())
                .findFirst();
        if (!entry.isPresent()) {
            return false;
        }
        LOGGER.debug("Potential includedNodePathPatternAndType {}", entry.get().toString());
        boolean isNodeRelevant = node.getPrimaryType().orElse("").equals(entry.get().getValue());
        if (!isNodeRelevant) {
            return false;
        } else {
            if (NameConstants.NT_PAGE.equals(entry.get().getValue())) {
                LOGGER.debug("Waiting for jcr:content below {}", nodePath);
                relevantPagePaths.add(nodePath);
                return false;
            } else {
                return true;
            }
        }
    }

    @Nullable
    public Collection<ValidationMessage> validate(@NotNull DocViewNode2 node, @NotNull NodeContext nodeContext, boolean isRoot) {
        if (isNodeRelevant(nodeContext.getNodePath(), node)) {
            return validate(node);
        }
        return null;
    }

    @Nullable
    public Collection<ValidationMessage> validateEnd(@NotNull DocViewNode2 node, @NotNull NodeContext nodeContext, boolean isRoot) {
        if (nodeContext.getNodePath().equals(relevantPagePaths.peek())) {
            LOGGER.debug("End waiting for jcr:content below {}", nodeContext.getNodePath());
            relevantPagePaths.poll();
        }
        return DocumentViewXmlValidator.super.validateEnd(node, nodeContext, isRoot);
    }

    private Calendar getLastReplicationDate(@NotNull DocViewNode2 node) throws IllegalStateException, RepositoryException {
        DocViewProperty2 property = Optional.ofNullable(node.getProperty(NAME_CQ_LAST_REPLICATED)
                .orElseGet(() -> node.getProperty(NAME_CQ_LAST_PUBLISHED)
                .orElse(null)))
            .orElseThrow(() -> new IllegalStateException("No replication property found"));
        Value lastReplicatedValue = valueFactory.createValue(
                property.getStringValue().orElseThrow(() -> new IllegalStateException("Empty replication property found in  " + property.getName())));
        return lastReplicatedValue.getDate();
    }

    private Collection<ValidationMessage> validate(@NotNull DocViewNode2 node) {
        Calendar lastReplicationDate;
        try {
            lastReplicationDate = getLastReplicationDate(node);
        } catch (IllegalStateException|RepositoryException e) {
            return Collections.singleton(new ValidationMessage(validationMessageSeverity, "No replication date found", e));
        }
        Calendar lastModificationDate;
        try {
            lastModificationDate = getLastModificationDate(node);
        } catch (IllegalStateException|RepositoryException e) {
            return Collections.singleton(new ValidationMessage(validationMessageSeverity, "No last modification date found", e));
        }
        
        // Logic from com.day.cq.wcm.core.impl.reference.converter.AssetJSONItemConverter.referenceToJSONObject()
        if (lastReplicationDate.compareTo(lastModificationDate) < 0) {
            return Collections.singleton(new ValidationMessage(validationMessageSeverity, "The replication date " + lastReplicationDate.toInstant().toString() + " is older than the last modification date " + lastModificationDate.toInstant().toString()));
        }
        return null;
    }

    private Calendar getLastModificationDate(@NotNull DocViewNode2 node) throws IllegalStateException, RepositoryException {
        final Calendar date;
        Optional<DocViewProperty2> property = Optional.ofNullable(node.getProperty(NAME_CQ_LAST_MODIFIED)
                .orElseGet(() -> node.getProperty(NAME_JCR_LAST_MODIFIED)
                .orElse(null)));
        if (property.isPresent()) {
            Value lastModifiedValue = valueFactory.createValue(property.get().getStringValue().orElseThrow(() -> new IllegalStateException("No value found in " + property.get().getName())), PropertyType.DATE);
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
        return date;
    }
}
