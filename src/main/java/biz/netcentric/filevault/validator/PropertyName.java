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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants;
import org.apache.jackrabbit.spi.Name;

import com.day.cq.commons.jcr.JcrConstants;

final class PropertyName {
    
    static final PropertyName PROPERTY_CQ_CREATED = new PropertyName(NameConstants.CQ_CREATED);
    static final PropertyName PROPERTY_CQ_LAST_MODIFIED = new PropertyName(NameConstants.CQ_LAST_MODIFIED);
    static final PropertyName PROPERTY_JCR_LAST_MODIFIED = new PropertyName(NameConstants.JCR_LASTMODIFIED, Arrays.asList(JcrConstants.NT_RESOURCE, NodeTypeConstants.NT_OAK_RESOURCE, JcrConstants.MIX_LAST_MODIFIED));
    static final PropertyName PROPERTY_JCR_CREATED = new PropertyName(NameConstants.JCR_CREATED, Arrays.asList(NameConstants.NT_CQ_PAGE_CONTENT, JcrConstants.MIX_CREATED));

    private final Name name;
    private final Collection<String> autoCreatedTypes; // either mixin or primary types

    private PropertyName(Name name) {
        this(name, Collections.emptySet());
    }

    private PropertyName(Name name, Collection<String> autoGenerateTypes) {
        super();
        this.name = name;
        this.autoCreatedTypes = autoGenerateTypes;
    }

    public Name getName() {
        return name;
    }

    public Collection<String> getAutoCreatedTypes() {
        return autoCreatedTypes;
    }
}