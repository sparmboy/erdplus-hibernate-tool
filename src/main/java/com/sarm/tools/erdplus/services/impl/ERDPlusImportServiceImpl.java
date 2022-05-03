package com.sarm.tools.erdplus.services.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarm.tools.erdplus.model.ERDPlusConnector;
import com.sarm.tools.erdplus.model.ERDPlusEntityRelationshipModel;
import com.sarm.tools.erdplus.model.ERDPlusShape;
import com.sarm.tools.erdplus.model.ERDPlusSlot;
import com.sarm.tools.erdplus.model.enums.RELATIONSHIP_TYPE;
import com.sarm.tools.erdplus.model.enums.TYPE;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.id.UUIDGenerator;

/**
 * Created by spencer on 16/07/2016.
 */
public class ERDPlusImportServiceImpl {

    private static final String CARDINALITY_ONE = "one";
    private static final String CARDINALITY_MANY = "many";

    public static void main(String[] args) throws IOException {
        if( args.length != 3 ) {
            System.out.println("Usage: java com.sarm.tools.erdplus.services.impl.ERDPlusImportServiceImpl <erdplus_export_file> <output_dir> <package_name>");
            System.exit(-1);
        }
        ERDPlusImportServiceImpl service = new ERDPlusImportServiceImpl(new File(args[1]),args[2]);
        ERDPlusEntityRelationshipModel model = service.readModel(new File(args[0]));
        service.generateEntityBeans(model);
        System.out.println("Done!");
    }

    ObjectMapper mapper = new ObjectMapper();
    private final File targetDir;
    private final String packageName;

    public ERDPlusImportServiceImpl(File targetDir, String packageName) {
        this.targetDir = targetDir;
        this.packageName = packageName;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ERDPlusEntityRelationshipModel readModel(File erModelExportFile) throws IOException {
        System.out.println("Reading model from " + erModelExportFile);
        return mapper.readValue(erModelExportFile, ERDPlusEntityRelationshipModel.class);
    }

    public File getTargetDir() {
        return targetDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public void generateEntityBeans(final ERDPlusEntityRelationshipModel model) {
        if (getTargetDir() == null) {
            throw new RuntimeException("No target directory specified");
        }
        if (!getTargetDir().exists()) {
            getTargetDir().mkdirs();
        }

        System.out.println("Generating entity beans for package " + packageName + " in " + targetDir);


        // Extract the entities
        List<ERDPlusShape> entities = extractTypesFromModel(model, TYPE.ENTITY);

        // Iterate the entities and write the bean for the entity
        entities.forEach(entity -> {
            try {
                writeEntityToFile(generateTypeSpecForEntity(model, entity));
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate entity bean for :" + e, e);
            }
        });
    }

    public List<ERDPlusShape> extractTypesFromModel(final ERDPlusEntityRelationshipModel model, TYPE type) {
        return model.shapes.stream()
            .filter(shape -> type.equals(shape.type))
            .collect(Collectors.toList());
    }

    public List<ERDPlusShape> extractRelationshipsForEntity(final ERDPlusEntityRelationshipModel model, int id) {
        return extractConnectorsForSource(model, id, TYPE.RELATIONSHIP_CONNECTOR).stream()
            .map(conn -> extractObjectFromModel(model, conn.destination))
            .collect(Collectors.toList());
    }

    private ERDPlusShape extractObjectFromModel(ERDPlusEntityRelationshipModel model, int entityId) {
        return model.shapes.stream()
            .filter(shape -> shape.details.id == entityId)    // we want "michael" only
            .findAny()                                    // If 'findAny' then return found
            .orElse(null);
    }

    public List<ERDPlusConnector> extractConnectorsForSource(final ERDPlusEntityRelationshipModel model, int sourceId, TYPE type) {
        return model.connectors.stream()
            .filter(connector -> connector.source == sourceId)
            .filter(connector -> connector.type == type)
            .collect(Collectors.toList());
    }


    /**
     * For the specified entity, this will create the class and add all the related entities to it
     * with annotated mappings based on the relationship it has with it
     *
     * @param model     The read in entity model
     * @param entity    The entity within the model to generate a class typespec for
     * @return          The generated type spec
     */
    private TypeSpec generateTypeSpecForEntity(final ERDPlusEntityRelationshipModel model, ERDPlusShape entity) {

        // Add the Table name
        AnnotationSpec.Builder tableAnon = AnnotationSpec.builder(Table.class);
        tableAnon.addMember("name", "$L", getAsClassName(entity.details.name) + ".TABLE_NAME");

        // Create a builder for a class that has the entities name and add an @Entity annotation
        TypeSpec.Builder builder = TypeSpec.classBuilder(getAsClassName(entity.details.name))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(tableAnon.build())
            .addAnnotation(Getter.class)
            .addAnnotation(Setter.class)
            .addAnnotation(Entity.class);

        // Create a TABLE_NAME field
        addTableNameFieldForEntity(entity, builder);

        // Create an ID field
        addIdentityFieldForEntity(builder);

        // Iterate all the relationships for this entity
        extractRelationshipsForEntity(model, entity.details.id).forEach(rel -> {

            RELATIONSHIP_TYPE type = getRelationshipTypeForRelationship(rel);

            rel.details.slots.forEach(slot -> {

                // Is this the foreign entity (i.e. we dont want to add a field for ourselves
                if (slot.entityId != entity.details.id) {
                    ERDPlusShape foreignEntity = extractObjectFromModel(model, slot.entityId);

                    // Add the field
                    addFieldForForeignEntity(
                        builder,
                        type,
                        foreignEntity,
                        slot,
                        entity,
                        rel.details.name
                    );
                }
            });
        });

        return builder.build();
    }

    private void addTableNameFieldForEntity(ERDPlusShape entity, TypeSpec.Builder builder) {
        String idFieldName = "TABLE_NAME";
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(String.class, idFieldName, Modifier.PUBLIC).addModifiers(Modifier.STATIC).addModifiers(Modifier.FINAL);
        fieldBuilder.initializer(CodeBlock.of("$S", entity.details.name.toUpperCase()));
        builder.addField(fieldBuilder.build());
    }

    /**
     * Auto generates an identity field to the entity
     *
     */
    private void addIdentityFieldForEntity(TypeSpec.Builder builder) {

        // Add the identity field
        String idFieldName = "id";
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(String.class, idFieldName, Modifier.PRIVATE);

        // Add the @Id
        fieldBuilder.addAnnotation(AnnotationSpec.builder(Id.class).build());

        // Add the id generation strategy
        AnnotationSpec.Builder anonBuilder = AnnotationSpec.builder(GeneratedValue.class);
        anonBuilder.addMember("generator", "$S", "UUID");
        fieldBuilder.addAnnotation(anonBuilder.build());

        // Add the generic genarator
        anonBuilder = AnnotationSpec.builder(GenericGenerator.class);
        anonBuilder.addMember("name", "$S", "UUID");
        anonBuilder.addMember("strategy", "$S", UUIDGenerator.class.getName());
        fieldBuilder.addAnnotation(anonBuilder.build());

        // Add the column definition
        anonBuilder =
            AnnotationSpec.builder(Column.class)
                .addMember("name", "$S", "ID")
                .addMember("unique", "true")
                .addMember("nullable", "false");
        fieldBuilder.addAnnotation(anonBuilder.build());

        // Add the return code block
        fieldBuilder.addJavadoc("$L", "The Database identifier for this record\n");

        builder.addField(fieldBuilder.build());

    }


    private void addFieldForForeignEntity(TypeSpec.Builder builder,
                                          RELATIONSHIP_TYPE type,
                                          ERDPlusShape foreignEntity,
                                          ERDPlusSlot slot,
                                          ERDPlusShape owningEntity,
                                          String relationshipDesc) {

        String foreignFieldName = getForeignEntityFieldName(foreignEntity);
        ClassName foreignClassType = ClassName.get(getPackageName(), getAsClassName(foreignEntity.details.name));


        // Determine if we add based on the relationship type
        // Check if we are the 'many' in a one-to-many relationship. If so we need to add the field
        // reference for who the 'one' we belong to is and annotate it
        if (RELATIONSHIP_TYPE.ONE_TO_MANY == type) {
            if (CARDINALITY_MANY.equals(slot.cardinality)) {
                FieldSpec.Builder fieldBuilder = FieldSpec.builder(foreignClassType, foreignFieldName, Modifier.PRIVATE);
                AnnotationSpec.Builder relationAnon = AnnotationSpec.builder(ManyToOne.class);
                AnnotationSpec.Builder joinAnon = AnnotationSpec.builder(JoinColumn.class);
                joinAnon.addMember("name", "$S", getIdColumnNameForEntity(foreignEntity));
                joinAnon.addMember("nullable", "$L", "false");
                fieldBuilder.addAnnotation(relationAnon.build());
                fieldBuilder.addAnnotation(joinAnon.build());
                fieldBuilder.addJavadoc("$L", relationshipDesc + "\n");
                builder.addField(fieldBuilder.build());
            } else if (CARDINALITY_ONE.equals(slot.cardinality)) {
                AnnotationSpec.Builder relationAnon = AnnotationSpec.builder(OneToMany.class);
                relationAnon.addMember("mappedBy", "$S", getAsFieldName(owningEntity.details.name));
                relationAnon.addMember("cascade", "$L", "CascadeType.All");
                generateSetFieldOfType(builder, foreignFieldName, foreignClassType, Collections.singletonList(relationAnon.build()), relationshipDesc);
            }
        }
        // For Many to Many
        else if (RELATIONSHIP_TYPE.MANY_TO_MANY == type) {
            List<AnnotationSpec> anons = new ArrayList<>();
            AnnotationSpec.Builder relationAnon = AnnotationSpec.builder(ManyToMany.class);
            relationAnon.addMember("mappedBy", "$S", getAsFieldName(owningEntity.details.name) + "s");
            anons.add(relationAnon.build());


            if (slot.slotIndex == 0) {
                AnnotationSpec.Builder joinAnon = AnnotationSpec.builder(JoinTable.class);
                joinAnon.addMember("name", "$S", getJoinTableName(foreignEntity,owningEntity));
                joinAnon.addMember("joinColumns", "$L", AnnotationSpec.builder(JoinColumn.class).addMember("name", "$S", getIdColumnNameForEntity(foreignEntity)).build());
                joinAnon.addMember("inverseJoinColumns", "$L", AnnotationSpec.builder(JoinColumn.class).addMember("name", "$S", getIdColumnNameForEntity(owningEntity)).build());
                anons.add(joinAnon.build());
            }

            generateSetFieldOfType(builder, foreignFieldName, foreignClassType, anons, relationshipDesc);
        }
        // For One to One
        else if (RELATIONSHIP_TYPE.ONE_TO_ONE == type) {

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(foreignClassType, foreignFieldName, Modifier.PRIVATE);

            // Add the join tables to the 1st half of the entity relationship
            if (slot.slotIndex == 0) {
                // Add the oneToone annotation
                AnnotationSpec.Builder anonBuilder = AnnotationSpec.builder(OneToOne.class);
                anonBuilder.addMember("fetch", "$L", javax.persistence.FetchType.LAZY);
                anonBuilder.addMember("cascade", "$L", javax.persistence.CascadeType.ALL);
                anonBuilder.addMember("mappedBy", "$S", getAsFieldName(owningEntity.details.name));
                fieldBuilder.addAnnotation(anonBuilder.build());
            } else if (slot.slotIndex == 1) {
                // Add the oneToMany annotation
                AnnotationSpec.Builder anonBuilder = AnnotationSpec.builder(OneToOne.class);
                anonBuilder.addMember("fetch", "$L", javax.persistence.FetchType.LAZY);
                fieldBuilder.addAnnotation(anonBuilder.build());

                // Add primary key join column annotation
                fieldBuilder.addAnnotation(AnnotationSpec.builder(PrimaryKeyJoinColumn.class).build());
            }


            builder.addField(fieldBuilder.build());
        }
    }

    private void generateSetFieldOfType(TypeSpec.Builder builder, String name, ClassName foreignClassType, List<AnnotationSpec> annotations, String relationshipDesc) {
        ClassName set = ClassName.get("java.util", "Set");
        TypeName setOfEntities = ParameterizedTypeName.get(set, foreignClassType);
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(setOfEntities, name + "s", Modifier.PRIVATE);
        annotations.forEach(fieldBuilder::addAnnotation);
        fieldBuilder.initializer(CodeBlock.of("new HashSet<>(0)"));
        fieldBuilder.addJavadoc("$L", relationshipDesc + "\n");
        builder.addField(fieldBuilder.build());
    }

    private String getForeignEntityFieldName(ERDPlusShape foreignEntity) {
        return getAsFieldName(foreignEntity.details.name);
    }


    private String getJoinTableName(ERDPlusShape entity, ERDPlusShape foreignEntity) {
        return entity.details.name.toUpperCase() + "_TO_" + foreignEntity.details.name.toUpperCase();
    }

    private String getIdColumnNameForEntity(ERDPlusShape entity) {
        return entity.details.name.toUpperCase() + "_ID";
    }


    private RELATIONSHIP_TYPE getRelationshipTypeForRelationship(ERDPlusShape rel) {
        if (rel.details.slots.get(0).cardinality.equals(CARDINALITY_ONE) && rel.details.slots.get(1).cardinality.equals(CARDINALITY_MANY)) {
            return RELATIONSHIP_TYPE.ONE_TO_MANY;
        }
        if (rel.details.slots.get(0).cardinality.equals(CARDINALITY_MANY) && rel.details.slots.get(1).cardinality.equals(CARDINALITY_ONE)) {
            return RELATIONSHIP_TYPE.ONE_TO_MANY;
        }
        if (rel.details.slots.get(0).cardinality.equals(CARDINALITY_ONE) && rel.details.slots.get(1).cardinality.equals(CARDINALITY_ONE)) {
            return RELATIONSHIP_TYPE.ONE_TO_ONE;
        }
        if (rel.details.slots.get(0).cardinality.equals(CARDINALITY_MANY) && rel.details.slots.get(1).cardinality.equals(CARDINALITY_MANY)) {
            return RELATIONSHIP_TYPE.MANY_TO_MANY;
        }

        throw new RuntimeException("Failed to identify relationship type for relationship: " + rel);
    }

    private void writeEntityToFile(TypeSpec spec) throws IOException {

        JavaFile javaFile = JavaFile.builder(getPackageName(), spec)
            .addStaticImport(GenerationType.class, "*")
            .addStaticImport(javax.persistence.FetchType.class, "*")
            .addStaticImport(javax.persistence.CascadeType.class, "*")
            .build();
        javaFile.writeTo(getTargetDir());
    }

    /**
     * Converts the given name into a valid class name
     *
     * @param name
     * @return
     */
    private String getAsClassName(String name) {

        if (isAllUpper(name)) {
            name = name.toLowerCase();
        }

        // Change Underscore notation
        if (name.contains("_")) {

            name = makeNextCharUpper(name, '_');

            // Remove underscores
            name = name.replaceAll("_", "");
        }
        // Change Space Notation
        else if (name.contains(" ")) {
            name = makeNextCharUpper(name, ' ');

            // Remove underscores
            name = name.replaceAll(" ", "");
        }

        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        return name;
    }

    /**
     * Converts the given name into a valid field name
     *
     * @param name
     * @return
     */
    private String getAsFieldName(String name) {

        name = getAsClassName(name);

        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Iterates the characters in the passed text and will set each character
     * after the found token to be uppercase
     *
     * @param text
     * @param token
     * @return
     */
    private String makeNextCharUpper(String text, char token) {
        // Make first and all letters after underscore upper
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i < chars.length - 1 && chars[i] == token) {
                chars[i + 1] = Character.toUpperCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Returns true if every character in the passed text is uppercase (ignoring whitespace)
     *
     * @param text
     * @return
     */
    private boolean isAllUpper(String text) {
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return true;
    }


}
