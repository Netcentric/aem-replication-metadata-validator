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

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a date property which can be extracted from a given {@link Node}.
 * It may have multiple fallbacks (i.e. tries to extract from multiple properties until one is found)
 */
enum DateProperty {
    
    
    /**
     * Extracts the date from
     * <ol>
     * <li>property {@code cq:lastModified} or</li>
     * <li>property {@code jcr:lastModified}</li>
     * </ol>
     */
    MODIFIED(Arrays.asList(PropertyName.PROPERTY_CQ_LAST_MODIFIED, PropertyName.PROPERTY_JCR_LAST_MODIFIED), false),
    /**
     * Extracts the date from
     * <ol>
     * <li>property {@code cq:lastModified}</li>
     * <li>property {@code jcr:lastModified}</li>
     * <li>property {@code jcr:created} or</li>
     * <li>the current date</li>
     * </ol>
     */
    MODIFIED_CREATED_OR_CURRENT(Arrays.asList(PropertyName.PROPERTY_CQ_LAST_MODIFIED, PropertyName.PROPERTY_JCR_LAST_MODIFIED, PropertyName.PROPERTY_JCR_CREATED), true),
       
    
    /**
     * Extracts the date from
     * <ol>
     * <li>property {@code cq:lastModified}</li> 
     * <li>property {@code cq:created} or</li>
     * <li>the current date</li>
     * </ol>
     */
    CQ_MODIFIED_CREATED_OR_CURRENT(Arrays.asList(PropertyName.PROPERTY_CQ_LAST_MODIFIED, PropertyName.PROPERTY_CQ_CREATED), true);

    private final boolean useCurrentDateAsLastResort;
    private final Collection<PropertyName> propertyNames;

    DateProperty(Collection<PropertyName> propertyNames, boolean useCurrentDateAsLastResort) {
        this.useCurrentDateAsLastResort = useCurrentDateAsLastResort;
        this.propertyNames = propertyNames;
    }

    /**
     * Extracts a date from a property given by the enum type.
     * @param node the node from which to extract the date
     * @return the date value from one of the properties specified by this enum together with a label (explaining where the date came from)
     * @throws RepositoryException 
     */
    Optional<Map.Entry<Calendar, String>> extractDate(@NotNull DocViewNode2 node) throws RepositoryException {
        Map.Entry<Calendar, String> dateAndLabel = extractDate(node, propertyNames).orElse(
                useCurrentDateAsLastResort ? new SimpleEntry<>(Calendar.getInstance(), "current date") : null);
        return Optional.ofNullable(dateAndLabel);
    }

    Optional<Map.Entry<Calendar, String>> extractDate(@NotNull DocViewNode2 node, Collection<PropertyName> propertyNames) throws RepositoryException {
        for (PropertyName propertyName : propertyNames) {
            Optional<Map.Entry<Calendar, String>> dateAndLabel = extractDate(node, propertyName);
            if (dateAndLabel.isPresent()) {
                return dateAndLabel;
            }
        }
        return Optional.empty();
    }

    Optional<Map.Entry<Calendar, String>> extractDate(@NotNull DocViewNode2 node, PropertyName propertyName) throws RepositoryException {
        Map.Entry<Calendar, String> dateAndLabel = null;
        Optional<DocViewProperty2> property = node.getProperty(propertyName.getName());
        if (property.isPresent()) {
            Value propertyValue = NameConstants.VALUE_FACTORY.createValue(property.get().getStringValue().orElseThrow(() -> new IllegalStateException("No value found in " + propertyName.getName())), PropertyType.DATE);
            dateAndLabel = new SimpleEntry<>(propertyValue.getDate(), property.get().getName().toString());
        } else {
            // check for auto-created property
            Collection<String> types = new HashSet<>();
            node.getPrimaryType().ifPresent(types::add);
            types.addAll(node.getMixinTypes());
            if (propertyName.getAutoCreatedTypes().stream().anyMatch(types::contains)) {
                dateAndLabel = new SimpleEntry<>(Calendar.getInstance(), "auto created " + propertyName.getName().toString());
            }
        }
        return Optional.ofNullable(dateAndLabel);
    }
}