package com.sarm.tools.erdplus.services.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.sarm.tools.erdplus.model.ERDPlusEntityRelationshipModel;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by spencer on 16/07/2016.
 */
public class ERDPlusImportServiceImplTest {

    public final static File TARGET_DIR = new File("target/test-output");

    ERDPlusImportServiceImpl service = new ERDPlusImportServiceImpl(TARGET_DIR, "com.togondo.domain.model");


    @Before
    public void initTargetDir() {
        try {
            FileUtils.deleteDirectory(TARGET_DIR);
        } catch (IOException e) {
        }
        TARGET_DIR.mkdirs();
    }

    @Test
    public void shouldReadModel() throws Exception {
        ERDPlusEntityRelationshipModel model = service.readModel(new File("src/test/resources/models/one_to_many_entity_model.json"));
        assertNotNull(model);
        assertEquals(5,model.shapes.size());
    }

    @Test
    public void shouldGenerateEntitiesForOneToMany() throws Exception {
        ERDPlusEntityRelationshipModel model = service.readModel(new File("src/test/resources/models/one_to_many_entity_model.json"));
        assertNotNull(model);

        service.generateEntityBeans(model);

        File classesDir = new File( TARGET_DIR.getAbsolutePath() + File.separator + service.getPackageName().replaceAll("\\.","\\" + File.separatorChar) );

        assertEquals(2, Objects.requireNonNull(classesDir.listFiles()).length);
        Arrays.asList(Objects.requireNonNull(classesDir.listFiles())).forEach(javaFile -> {
            CompilationUnit genClass = parseJavaFile(javaFile, service.getPackageName() );
            genClass.getTypes();
        });
    }

    @Test
    public void shouldGenerateEntitiesForManyToMany() throws Exception {
        ERDPlusEntityRelationshipModel model = service.readModel(new File("src/test/resources/models/many_to_many_entity_model.json"));
        assertNotNull(model);

        service.generateEntityBeans(model);

        assertEquals(1, Objects.requireNonNull(TARGET_DIR.listFiles()).length);
        Arrays.asList(Objects.requireNonNull(TARGET_DIR.listFiles())).forEach(classFile -> {

        });
    }

    @Test
    public void shouldGenerateEntitiesForOneToOne() throws Exception {
        ERDPlusEntityRelationshipModel model = service.readModel(new File("src/test/resources/models/one_to_one_entity_model.json"));
        assertNotNull(model);

        service.generateEntityBeans(model);

        assertEquals(1, Objects.requireNonNull(TARGET_DIR.listFiles()).length);
        Arrays.asList(Objects.requireNonNull(TARGET_DIR.listFiles())).forEach(classFile -> {

        });
    }

    private CompilationUnit parseJavaFile( File file, String packageName ) {
        InputStream in = null;
        CompilationUnit cu = null;
        try
        {
            in = new FileInputStream(file );
            cu = JavaParser.parse(in);
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        return cu;
    }
}