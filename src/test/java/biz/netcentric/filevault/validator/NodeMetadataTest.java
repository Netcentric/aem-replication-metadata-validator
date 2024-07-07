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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeMetadataTest {

    @Test
    void testGetPath() {
        NodeMetadata nodeMetadata = new NodeMetadata(true, "my/path", false, DateProperty.MODIFIED);
        assertEquals("my/path", nodeMetadata.getPath());
        assertTrue(nodeMetadata.isExcluded);
    }

}
