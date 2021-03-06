/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.integrationtests.drl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.drools.compiler.Cheese;
import org.drools.compiler.Cheesery;
import org.drools.compiler.CommonTestMethodBase;
import org.drools.compiler.FirstClass;
import org.drools.compiler.Person;
import org.drools.compiler.SecondClass;
import org.drools.compiler.integrationtests.SerializationHelper;
import org.drools.core.impl.InternalKnowledgeBase;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ImportsTest extends CommonTestMethodBase {

    private static Logger logger = LoggerFactory.getLogger(ImportsTest.class);

    @Test
    public void testImportFunctions() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBase("test_ImportFunctions.drl"));
        KieSession session = createKnowledgeSession(kbase);

        final Cheese cheese = new Cheese("stilton",
                15);
        session.insert(cheese);
        List list = new ArrayList();
        session.setGlobal("list", list);
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);
        final int fired = session.fireAllRules();

        list = (List) session.getGlobal("list");

        assertEquals(4, fired);
        assertEquals(4, list.size());

        assertEquals("rule1", list.get(0));
        assertEquals("rule2", list.get(1));
        assertEquals("rule3", list.get(2));
        assertEquals("rule4", list.get(3));
    }

    @Test()
    public void testImport() throws Exception {
        // Same package as this test
        String rule = "";
        rule += "package org.drools.compiler.integrationtests;\n";
        rule += "import java.lang.Math;\n";
        rule += "rule \"Test Rule\"\n";
        rule += "  dialect \"mvel\"\n";
        rule += "  when\n";
        rule += "  then\n";
        // Can't handle the TestFact.TEST
        rule += "    new TestFact(TestFact.TEST);\n";
        rule += "end";

        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBaseFromString(rule));
        final KieSession ksession = createKnowledgeSession(kbase);
        ksession.fireAllRules();
    }

    @Test
    public void testImportColision() throws Exception {
        final Collection<KiePackage> kpkgs1 = loadKnowledgePackages("nested1.drl");
        final Collection<KiePackage> kpkgs2 = loadKnowledgePackages("nested2.drl");
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) loadKnowledgeBase();
        kbase.addPackages(kpkgs1);
        kbase.addPackages(kpkgs2);

        final KieSession ksession = createKnowledgeSession(kbase);

        SerializationHelper.serializeObject(kbase);

        ksession.insert(new FirstClass());
        ksession.insert(new SecondClass());
        ksession.insert(new FirstClass.AlternativeKey());
        ksession.insert(new SecondClass.AlternativeKey());

        ksession.fireAllRules();
    }

    @Test
    public void testImportConflict() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBase("test_ImportConflict.drl"));
        createKnowledgeSession(kbase);
    }

    @Test
    public void testMissingImport() throws Exception {
        String str = "";
        str += "package org.drools.compiler \n";
        str += "import " + Person.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "rule rule1 \n";
        str += "when \n";
        str += "    $i : Cheese() \n";
        str += "         MissingClass( fieldName == $i ) \n";
        str += "then \n";
        str += "    list.add( $i ); \n";
        str += "end \n";

        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add(ResourceFactory.newByteArrayResource(str.getBytes()),
                ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            logger.warn(kbuilder.getErrors().toString());
        }
        assertTrue(kbuilder.hasErrors());
    }

    @Test
    public void testMissingImports() {
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newClassPathResource( "test_missing_import.drl",
                getClass() ),
                ResourceType.DRL );
        assertTrue( kbuilder.hasErrors() );
    }

    @Test
    public void testPackageImportWithMvelDialect() throws Exception {
        // JBRULES-2244
        final String str = "package org.drools.compiler.test;\n" +
                "import org.drools.compiler.*\n" +
                "dialect \"mvel\"\n" +
                "rule R1 no-loop when\n" +
                "   $p : Person( )" +
                "   $c : Cheese( )" +
                "then\n" +
                "   modify($p) { setCheese($c) };\n" +
                "end\n";

        final KieBase kbase = loadKnowledgeBaseFromString( str );
        final KieSession ksession = kbase.newKieSession();

        final Person p = new Person( "Mario", 38 );
        ksession.insert( p );
        final Cheese c = new Cheese( "Gorgonzola" );
        ksession.insert( c );

        assertEquals( 1, ksession.fireAllRules() );
        assertSame( c, p.getCheese() );
    }

    @Test
    public void testImportStaticClass() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBase("test_StaticField.drl"));
        KieSession session = createKnowledgeSession(kbase);

        // will test serialisation of int and typesafe enums tests
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);

        final List list = new ArrayList();
        session.setGlobal("list", list);

        final Cheesery cheesery1 = new Cheesery();
        cheesery1.setStatus(Cheesery.SELLING_CHEESE);
        cheesery1.setMaturity(Cheesery.Maturity.OLD);
        session.insert(cheesery1);
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);

        final Cheesery cheesery2 = new Cheesery();
        cheesery2.setStatus(Cheesery.MAKING_CHEESE);
        cheesery2.setMaturity(Cheesery.Maturity.YOUNG);
        session.insert(cheesery2);
        session = SerializationHelper.getSerialisedStatefulKnowledgeSession(session, true);

        session.fireAllRules();

        assertEquals(2, list.size());

        assertEquals(cheesery1, list.get(0));
        assertEquals(cheesery2, list.get(1));
    }

    public static class StaticMethods {

        public static String getString1(final String string) {
            return string;
        }

        public static String getString2(final String string) {
            return string;
        }

    }

    public static class StaticMethods2 {
        public static String getString3(final String string, final Integer integer) {
            return string + integer;
        }
    }

    @Test
    public void testImportInnerFunctions() {

        final String drl = "package org.drools.compiler.integrationtests.drl;\n" +
                "import function " + StaticMethods.class.getCanonicalName() + ".*;\n" +
                "import function " + StaticMethods2.class.getCanonicalName() + ".getString3;\n" +
                "import " + Cheese.class.getCanonicalName() + ";\n" +
                "global java.util.List list;\n" +
                "\n" +
                "function String getString4( String string ) {\n" +
                "    return string;\n" +
                "}\n" +
                "\n" +
                "rule \"test rule1\"\n" +
                "    salience 30\n" +
                "    when\n" +
                "        Cheese()\n" +
                "    then\n" +
                "        list.add( getString1( \"rule1\" ) );\n" +
                "end    \n" +
                "\n" +
                "rule \"test rule2\"\n" +
                "    salience 20\n" +
                "    when\n" +
                "        Cheese( type == ( getString2(\"stilton\") ) );\n" +
                "    then\n" +
                "        list.add( getString3( \"rule\", new Integer( 2 ) ) );\n" +
                "end    \n" +
                "\n" +
                "rule \"test rule3\"\n" +
                "    salience 10\n" +
                "    when\n" +
                "        Cheese( $type : type);\n" +
                "        eval( $type.equals( getString1( \"stilton\" ) ) );\n" +
                "    then\n" +
                "        list.add( getString2( \"rule3\" ) );\n" +
                "end    \n" +
                "\n" +
                "rule \"test rule4\"\n" +
                "    salience 0\n" +
                "    when\n" +
                "        Cheese();\n" +
                "    then\n" +
                "        list.add( getString4( \"rule4\" ) );\n" +
                "end";

        KieSession ksession = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();

        try {
            final Cheese cheese = new Cheese("stilton",
                    15);
            ksession.insert(cheese);
            List list = new ArrayList();
            ksession.setGlobal("list", list);
            final int fired = ksession.fireAllRules();

            list = (List) ksession.getGlobal("list");

            assertEquals(4, fired);
            assertEquals(4, list.size());

            assertEquals("rule1", list.get(0));
            assertEquals("rule2", list.get(1));
            assertEquals("rule3", list.get(2));
            assertEquals("rule4", list.get(3));
        } finally {
            ksession.dispose();
        }
    }
}
