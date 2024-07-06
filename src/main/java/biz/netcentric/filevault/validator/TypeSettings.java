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

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Settings for a particular node type which requires replicaton metadata
 */
public class TypeSettings {

    static final @NotNull Name NAME_SLING_RESOURCETYPE = NodeMetadata.NAME_FACTORY.create(JcrResourceConstants.SLING_NAMESPACE_URI, SlingConstants.PROPERTY_RESOURCE_TYPE);
    static final @NotNull String NT_CQ_PAGE_CONTENT = "cq:PageContent";

    enum ComparisonDate {
        JCR_CQ_LASTMODIFIED,
        JCR_CQ_LASTMODIFIED_JCR_CREATED;
    }
    private final @NotNull Pattern pathPattern;
    private final @NotNull String type; // either primary type or resource type
    private @NotNull ComparisonDate comparisonDate;

    public TypeSettings(@NotNull String pathPattern, @NotNull String type) {
        this.pathPattern = Pattern.compile(pathPattern);
        this.type = type;
        this.comparisonDate = ComparisonDate.JCR_CQ_LASTMODIFIED;
    }

    public void setReferenceDate(@NotNull ComparisonDate comparisonDate) {
        this.comparisonDate = comparisonDate;
    }

    public boolean matches(@NotNull String actualNodePath, @NotNull DocViewNode2 node) {
        if (!pathPattern.matcher(actualNodePath).matches()) {
            return false;
        }
        String actualPrimaryType = node.getPrimaryType().orElse("");
        boolean isNodeRelevant = actualPrimaryType.equals(type);
        // if node type == nt:unstructured or cq:PageContent, evaluate sling:resourceType in addition
        if (!isNodeRelevant && (actualPrimaryType.equals(JcrConstants.NT_UNSTRUCTURED) || actualPrimaryType.equals(NT_CQ_PAGE_CONTENT))) {
            isNodeRelevant = node.getPropertyValue(NAME_SLING_RESOURCETYPE).equals(Optional.of(type));
        }
        return isNodeRelevant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonDate, pathPattern, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof TypeSettings))
            return false;
        TypeSettings other = (TypeSettings) obj;
        return comparisonDate == other.comparisonDate && Objects.equals(pathPattern.pattern(), other.pathPattern.pattern())
                && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
        return "TypeSettings [resourcePathPattern=" + pathPattern + ", type=" + type + ", comparisonDate=" + comparisonDate + "]";
    }
}
