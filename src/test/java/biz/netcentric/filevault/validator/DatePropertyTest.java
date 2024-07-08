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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.junit.jupiter.api.Test;

public class DatePropertyTest {

    public static final String ISO8601_DATE1 = "1970-01-01T01:00:10.000+01:00";
    public static final String ISO8601_DATE2 = "2022-01-02T00:00:00.000+01:00";
    public static final String ISO8601_DATE3 = "2024-01-02T00:00:00.000+01:00";
    
    @Test
    void testExtractDateModified() throws IllegalStateException, RepositoryException {
        DocViewProperty2 lastModificationProperty = DocViewProperty2.parse(NameConstants.JCR_LASTMODIFIED, "{Date}"+ISO8601_DATE1);
        DocViewProperty2 lastModificationProperty2 = DocViewProperty2.parse(NameConstants.CQ_LAST_MODIFIED, "{Date}"+ISO8601_DATE2);
        DocViewNode2 node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Arrays.asList(lastModificationProperty, lastModificationProperty2));

        Optional<Map.Entry<Calendar, String>> result = DateProperty.MODIFIED.extractDate(node);
        assertTrue(result.isPresent());
        assertEquals(ISO8601.parse(ISO8601_DATE2).toInstant(), result.get().getKey().toInstant());
        assertEquals("{http://www.day.com/jcr/cq/1.0}lastModified", result.get().getValue());

        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.singleton(lastModificationProperty));
        result = DateProperty.MODIFIED.extractDate(node);
        assertTrue(result.isPresent());
        assertEquals(ISO8601.parse(ISO8601_DATE1).toInstant(), result.get().getKey().toInstant());
        assertEquals("{http://www.jcp.org/jcr/1.0}lastModified", result.get().getValue());

        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.emptySet());
        assertFalse(DateProperty.MODIFIED.extractDate(node).isPresent());

        // check auto-created property
        DocViewProperty2 primaryTypeProperty = DocViewProperty2.parse(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_PRIMARYTYPE, "nt:resource");
        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.singleton(primaryTypeProperty));
        result = DateProperty.MODIFIED.extractDate(node);
        assertTrue(result.isPresent());
        long delta = Duration.between(Instant.now(), result.get().getKey().toInstant()).toMillis();
        assertTrue(delta < 100);
        assertEquals("auto created jcr:lastModified", result.get().getValue());
    }

    @Test
    void testExtractDateModifiedCreatedOrCurrent() throws IllegalStateException, RepositoryException {
        DocViewProperty2 lastModificationProperty = DocViewProperty2.parse(NameConstants.JCR_LASTMODIFIED, "{Date}"+ISO8601_DATE1);
        DocViewProperty2 lastModificationProperty2 = DocViewProperty2.parse(NameConstants.CQ_LAST_MODIFIED, "{Date}"+ISO8601_DATE2);
        DocViewProperty2 createdProperty = DocViewProperty2.parse(NameConstants.JCR_CREATED, "{Date}"+ISO8601_DATE3);
        DocViewNode2 node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Arrays.asList(createdProperty, lastModificationProperty, lastModificationProperty2));

        Optional<Map.Entry<Calendar, String>> result = DateProperty.MODIFIED_CREATED_OR_CURRENT.extractDate(node);
        assertTrue(result.isPresent());
        assertEquals(ISO8601.parse(ISO8601_DATE2).toInstant(), result.get().getKey().toInstant());
        assertEquals("{http://www.day.com/jcr/cq/1.0}lastModified", result.get().getValue());

        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.singleton(lastModificationProperty));
        result = DateProperty.MODIFIED_CREATED_OR_CURRENT.extractDate(node);
        assertTrue(result.isPresent());
        assertEquals(ISO8601.parse(ISO8601_DATE1).toInstant(), result.get().getKey().toInstant());
        assertEquals("{http://www.jcp.org/jcr/1.0}lastModified", result.get().getValue());

        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.singleton(createdProperty));
        result = DateProperty.MODIFIED_CREATED_OR_CURRENT.extractDate(node);
        assertTrue(result.isPresent());
        assertEquals(ISO8601.parse(ISO8601_DATE3).toInstant(), result.get().getKey().toInstant());
        assertEquals("{http://www.jcp.org/jcr/1.0}created", result.get().getValue());

        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.emptySet());
        result = DateProperty.MODIFIED_CREATED_OR_CURRENT.extractDate(node);
        assertTrue(result.isPresent());
        long delta = Duration.between(Instant.now(), result.get().getKey().toInstant()).toMillis();
        assertTrue(delta < 100);
        assertEquals("current date", result.get().getValue());

        // check auto-created property
        DocViewProperty2 primaryTypeProperty = DocViewProperty2.parse(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_PRIMARYTYPE, "cq:PageContent");
        node = new DocViewNode2(org.apache.jackrabbit.spi.commons.name.NameConstants.JCR_ROOT, Collections.singleton(primaryTypeProperty));
        result = DateProperty.MODIFIED_CREATED_OR_CURRENT.extractDate(node);
        assertTrue(result.isPresent());
        delta = Duration.between(Instant.now(), result.get().getKey().toInstant()).toMillis();
        assertTrue(delta < 100);
        assertEquals("auto created jcr:created", result.get().getValue());
    }
}
