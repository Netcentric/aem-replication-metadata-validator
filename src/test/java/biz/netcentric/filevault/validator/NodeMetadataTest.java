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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.junit.jupiter.api.Test;

class NodeMetadataTest {

    @Test
    void testGetPath() {
        NodeMetadata nodeMetadata = new NodeMetadata(true, "my/path", false);
        assertEquals("my/path", nodeMetadata.getPath());
        assertTrue(nodeMetadata.isExcluded);
    }

    @Test
    void testCaptureLastModificationDate() throws IllegalStateException, RepositoryException {
        NodeMetadata nodeMetadata = new NodeMetadata(false, "my/path", false);
        assertFalse(nodeMetadata.isExcluded);
        
        DocViewProperty2 lastModificationProperty = DocViewProperty2.parse(NameConstants.JCR_LASTMODIFIED, "{Date}1970-01-01T01:00:10.000+01:00");
        DocViewNode2 node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(lastModificationProperty));
        nodeMetadata.captureLastModificationDate(node, false);
        Instant expectedInstant = Instant.ofEpochSecond(10);
        assertEquals(expectedInstant, nodeMetadata.getLastModificationDate().get().toInstant());
        
        lastModificationProperty = DocViewProperty2.parse(NodeMetadata.NAME_CQ_LAST_MODIFIED, "{Date}2022-01-02T00:00:00.000+01:00");
        nodeMetadata.captureLastModificationDate(node, false);
        node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.singleton(lastModificationProperty));
        assertEquals(expectedInstant, nodeMetadata.getLastModificationDate().get().toInstant());
        
        node = new DocViewNode2(NameConstants.JCR_ROOT, Collections.emptySet());
        nodeMetadata.captureLastModificationDate(node, false);
        assertTrue(Calendar.getInstance().compareTo(nodeMetadata.getLastModificationDate().get()) > 0);
        
        nodeMetadata.captureLastModificationDate(node, true);
        assertTrue(Calendar.getInstance().compareTo(nodeMetadata.getLastModificationDate().get()) < 0);
    }

}
