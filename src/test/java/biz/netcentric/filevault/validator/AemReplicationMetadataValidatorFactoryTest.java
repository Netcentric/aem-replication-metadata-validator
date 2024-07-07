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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AemReplicationMetadataValidatorFactoryTest {

    @Test
    void testParseTypesSettings() {
        Assertions.assertThat(AemReplicationMetadataValidatorFactory.parseTypesSettings("test[cq:Page]"))
            .containsExactly(new TypeSettings("test", "cq:Page"));
        TypeSettings complexTypeSettings = new TypeSettings("anotherregex", "nt:unstructured");
        complexTypeSettings.setComparisonDatePropery(DateProperty.MODIFIED_CREATED_OR_CURRENT);
        Assertions.assertThat(AemReplicationMetadataValidatorFactory.parseTypesSettings("test[cq:Page],anotherregex[nt:unstructured];comparisonDate=MODIFIED_CREATED_OR_CURRENT"))
            .containsExactly(new TypeSettings("test", "cq:Page"), complexTypeSettings);
        
        // test with whitespace after comma
        Assertions.assertThat(AemReplicationMetadataValidatorFactory.parseTypesSettings("test[cq:Page], anotherregex[nt:unstructured];comparisonDate=MODIFIED_CREATED_OR_CURRENT"))
        .containsExactly(new TypeSettings("test", "cq:Page"), complexTypeSettings);
    }

}
