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

import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class AemReplicationMetadataValidatorFactoryTest {

    @Test
    void test() {
        // due to https://bugs.openjdk.org/browse/JDK-7163589 need custom condition for the pattern
        Assertions.assertThat(AemReplicationMetadataValidatorFactory.parseNodePathPatternsAndTypes("test[cq:Page]"))
            .hasEntrySatisfying(new Condition<Pattern>(p -> p.pattern().equals("test"), "is pattern 'test'" ),
                    new Condition<String>(s -> s.equals("cq:Page"), "is a page"))
            .hasSize(1);
    }

}
