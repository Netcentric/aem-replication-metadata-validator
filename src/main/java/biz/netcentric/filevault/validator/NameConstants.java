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

import javax.jcr.ValueFactory;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;

public class NameConstants {

    private NameConstants() {
        
    }

    public static final NameFactory NAME_FACTORY = NameFactoryImpl.getInstance();
    public static final ValueFactory VALUE_FACTORY = ValueFactoryImpl.getInstance();

    public static final String CQ_NAMESPACE_URI = "http://www.day.com/jcr/cq/1.0"; // no constant defined in https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc/constant-values.html

    public static final @NotNull Name SLING_RESOURCETYPE = NAME_FACTORY.create(JcrResourceConstants.SLING_NAMESPACE_URI, SlingConstants.PROPERTY_RESOURCE_TYPE);
    public static final @NotNull String NT_CQ_PAGE_CONTENT = "cq:PageContent";

    public static final @NotNull Name CQ_LAST_MODIFIED = NAME_FACTORY.create(CQ_NAMESPACE_URI, "lastModified");
    public static final @NotNull Name CQ_CREATED = NAME_FACTORY.create(CQ_NAMESPACE_URI, "created");
    public static final @NotNull Name JCR_LASTMODIFIED = org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_LASTMODIFIED;
    public static final @NotNull Name JCR_CREATED = org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_CREATED;
    
}
