/*
 *
 * Copyright (c) 2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.starter.application.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MermaidUtilNoiseTest {

    private final String EXPECTED_OUTPUT = """
                                           erDiagram
                                               ATTACHMENT {
                                                   int attachmentId PK
                                                   string fileName
                                                   string fileType
                                                   string fileSize
                                                   string uploadDate
                                               }
                                           """;

    @Test
    void testSplitAttributeName() {
        // Test input for the first ATTACHMENT diagram
        String input = """
                       erDiagram
                           ATTACHMENT {
                               int attachmentId PK
                               string fileName
                               string fileType
                               string fileSize
                               string upload Date
                           }""";

        String actualOutput = MermaidUtil.filterNoise(input);
        assertEquals(EXPECTED_OUTPUT.trim(), actualOutput.trim());
    }

    @Test
    void testCommentedAttribute() {
        String input = """
                       erDiagram
                           ATTACHMENT {
                               int attachmentId PK
                               string fileName
                               string fileType
                               string fileSize // The file size
                               string uploadDate
                           }""";

        String actualOutput = MermaidUtil.filterNoise(input);
        assertEquals(EXPECTED_OUTPUT.trim(), actualOutput.trim());
    }

    @Test
    void testCommentedSplitAttributeName() {
        String input = """
                       erDiagram
                           ATTACHMENT {
                               int attachmentId PK
                               string fileName
                               string fileType
                               string fileSize
                               string upload Date // the upload data
                           }""";

        String actualOutput = MermaidUtil.filterNoise(input);
        assertEquals(EXPECTED_OUTPUT.trim(), actualOutput.trim());
    }

    @Test
    void testHyphenNamedClass() {
        String input = """
                                            erDiagram
                                               INCIDENT {
                                                   int incidentId PK
                                                   string status
                                               }
                                               IT-OPERATION {
                                                   long operationId PK
                                                   string description
                                               }
                                           """;
        String expected = """
                                            erDiagram
                                               INCIDENT {
                                                   int incidentId PK
                                                   string status
                                               }
                                               IT_OPERATION {
                                                   long operationId PK
                                                   string description
                                               }
                                           """;

        String actualOutput = MermaidUtil.filterNoise(input);
        assertEquals(expected.trim(), actualOutput.trim());
    }

    @Test
    void testSplitedRelationName() {
        String input = """
                       erDiagram
                           INCIDENT {
                               int incidentID PK
                               string status
                               string priority
                           }
                           INCIDENT ||--o{ CATEGORY : belongs to
                           CATEGORY {
                               int categoryID PK
                               string categoryName
                               string description
                           }
                           p ||--o| a : has
                           PERSON ||--o{ NAMED-DRIVER : is not
                           JOB_APPLICATION ||--|{ JOB : applies_for
                       """;

        String expected = """
                       erDiagram
                           INCIDENT {
                               int incidentID PK
                               string status
                               string priority
                           }
                           INCIDENT ||--o{ CATEGORY : belongsTo
                           CATEGORY {
                               int categoryID PK
                               string categoryName
                               string description
                           }
                           p ||--o| a : has
                           PERSON ||--o{ NAMED_DRIVER : isNot
                           JOB_APPLICATION ||--|{ JOB : applies_for
                        """;

        String actualOutput = MermaidUtil.filterNoise(input);
        assertEquals(expected.trim(), actualOutput.trim());
    }

}
