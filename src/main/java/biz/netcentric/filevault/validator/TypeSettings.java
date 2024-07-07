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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.jetbrains.annotations.NotNull;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Settings for a particular node type which requires replicaton metadata
 */
public class TypeSettings {

    private final @NotNull Pattern pathPattern;
    private final @NotNull String type; // either primary type or resource type
    private @NotNull DateProperty comparisonDateProperty;

    public TypeSettings(@NotNull String pathPattern, @NotNull String type) {
        this.pathPattern = Pattern.compile(pathPattern);
        this.type = type;
        this.comparisonDateProperty = DateProperty.MODIFIED;
    }

    public void setComparisonDatePropery(@NotNull DateProperty dateProperty) {
        this.comparisonDateProperty = dateProperty;
    }

    public boolean matches(@NotNull String actualNodePath, @NotNull DocViewNode2 node) {
        if (!pathPattern.matcher(actualNodePath).matches()) {
            return false;
        }
        String actualPrimaryType = node.getPrimaryType().orElse("");
        boolean isNodeRelevant = actualPrimaryType.equals(type);
        // if node type == nt:unstructured or cq:PageContent, evaluate sling:resourceType in addition
        if (!isNodeRelevant && (actualPrimaryType.equals(JcrConstants.NT_UNSTRUCTURED) || actualPrimaryType.equals(NameConstants.NT_CQ_PAGE_CONTENT))) {
            isNodeRelevant = node.getPropertyValue(NameConstants.SLING_RESOURCETYPE).equals(Optional.of(type));
        }
        return isNodeRelevant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonDateProperty, pathPattern, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof TypeSettings))
            return false;
        TypeSettings other = (TypeSettings) obj;
        return comparisonDateProperty == other.comparisonDateProperty && Objects.equals(pathPattern.pattern(), other.pathPattern.pattern())
                && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
        return "TypeSettings [resourcePathPattern=" + pathPattern + ", type=" + type + ", comparisonDate=" + comparisonDateProperty + "]";
    }

    public DateProperty getComparisonDate() {
        return comparisonDateProperty;
    }
}
