/*-
 * #%L
 * AEM Replication Metadata Validator
 * %%
 * Copyright (C) 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */
package biz.netcentric.filevault.validator;

import java.util.Calendar;
import java.util.Map;
import java.util.Optional;
import java.util.AbstractMap.SimpleEntry;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.jetbrains.annotations.NotNull;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Represents a date property which can be extracted from a given {@link Node}.
 * It may have multiple fallbacks (i.e. tries to extract from multiple properties until one is found)
 */
enum DateProperty {
    /**
     * Extracts the date from
     * <ol>
     * <li>property {@code cq:lastModified}</li> or
     * <li>property {@code jcr:lastModified}</li>
     * </ol>
     */
    MODIFIED(false, false),
    /**
     * Extracts the date from
     * <ol>
     * <li>property {@code cq:lastModified}</li>
     * <li>property {@code jcr:lastModified}</li>
     * <li>property {@code jcr:created}</li>
     * <li>the current date</li>
     * </ol>
     */
    MODIFIED_CREATED_OR_CURRENT(true, true);

    private final boolean useCreatedProperty;
    boolean useCurrentDateAsLastResort;

    DateProperty(boolean useCreatedProperty, boolean useCurrentDateAsLastResort) {
        this.useCreatedProperty = useCreatedProperty;
        this.useCurrentDateAsLastResort = useCurrentDateAsLastResort;
    }

    /**
     * Extracts a date from a property given by the enum type.
     * @param node the node from which to extract the date
     * @return the date value from one of the properties specified by this enum together with a label (explaining where the date came from)
     * @throws RepositoryException 
     */
    Optional<Map.Entry<Calendar, String>> extractDate(@NotNull DocViewNode2 node) throws RepositoryException {
        Map.Entry<Calendar, String> dateAndLabel = null;
        Optional<DocViewProperty2> property = Optional.ofNullable(node.getProperty(NameConstants.CQ_LAST_MODIFIED)
                .orElseGet(() -> 
                    node.getProperty(NameConstants.JCR_LASTMODIFIED)
                        .orElse(null)));
        if (!property.isPresent()) {
            // auto-created property?
            if (node.getPrimaryType().orElse("").equals(JcrConstants.NT_RESOURCE) 
                    || node.getPrimaryType().orElse("").equals(NodeTypeConstants.NT_OAK_RESOURCE)
                    || node.getMixinTypes().contains(JcrConstants.MIX_LAST_MODIFIED)) {
                dateAndLabel = new SimpleEntry<>(Calendar.getInstance(), "auto created jcr:lastModified");
            }
        }
        if (useCreatedProperty && !property.isPresent()) {
            property = node.getProperty(NameConstants.JCR_CREATED);
            // implicitly created?
            if (!property.isPresent()) {
                // auto-created property?
                if (node.getPrimaryType().orElse("").equals(NameConstants.NT_CQ_PAGE_CONTENT) 
                        || node.getMixinTypes().contains(JcrConstants.MIX_CREATED)) {
                    dateAndLabel = new SimpleEntry<>(Calendar.getInstance(), "auto created jcr:created");
                }
            }
        }
        
        if (property.isPresent()) {
            String propertyName = property.get().getName().toString();
            Value propertyValue = NameConstants.VALUE_FACTORY.createValue(property.get().getStringValue().orElseThrow(() -> new IllegalStateException("No value found in " + propertyName)), PropertyType.DATE);
            dateAndLabel = new SimpleEntry<>(propertyValue.getDate(), property.get().getName().toString());
        }
        if (dateAndLabel == null && useCurrentDateAsLastResort) {
            dateAndLabel = new SimpleEntry<>(Calendar.getInstance(), "current date");
        }
        return Optional.ofNullable(dateAndLabel);
    }
}