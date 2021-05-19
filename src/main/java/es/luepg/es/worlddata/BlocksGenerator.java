package es.luepg.es.worlddata;

import com.google.googlejavaformat.java.FormatterException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


/**
 * Maven plugin that generates the Blocks and BlockStates from minecraft reports
 *
 * @author MisterErwin
 */
@Mojo(name = "genblocks", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)

public class BlocksGenerator extends AbstractMojo {

  @Parameter(property = "gen.targetDirectory", defaultValue = "${project.basedir}/target/generated-sources", required = true)
  private File sourceDirectory;

  @Parameter(property = "gen.report.block", defaultValue = "${project.basedir}/src/main/resources/reports/blocks.json", required = true)
  private File blockReportFile;

  private final String packageName = "es.luepg.mcdata";
  @Parameter(property = "gen.packageName", defaultValue = "es.luepg.ecs")
  private String packageNameImpl;


  @Parameter(defaultValue = "${project}")
  private org.apache.maven.project.MavenProject project;


  public static void main(String[] a) throws Exception {
    // ToDO: A non-maven usage?
  }

  public void execute() throws MojoExecutionException {
    try {
      this.doGenerate();
    } catch (Exception e) {
      e.printStackTrace();
      throw new MojoExecutionException("Failed to generate", e);
    }
  }

  // TODO: The code is currently near unreadable - maybe load reports via GSON, etc..
  private void doGenerate() throws IOException, FormatterException {
    System.out.println("Using reports from " + blockReportFile.getAbsolutePath());
    System.out.println("Outputting source to " + sourceDirectory.getAbsolutePath());
    project.addCompileSourceRoot(sourceDirectory.getPath());

    StringBuilder classOutput = new StringBuilder();
    StringBuilder methodOutput = new StringBuilder();

    classOutput.append("package " + packageName + ".data.game.world.block;").append(System.lineSeparator());
    classOutput.append("import ").append(packageName).append(".data.game.world.block.property.*;")
            .append(System.lineSeparator());
    classOutput.append("import ").append(packageNameImpl).append(".data.game.world.block.property.*;")
            .append(System.lineSeparator());
    classOutput.append("import static " + packageName + ".data.game.world.block.BlockDef.*;")
            .append(System.lineSeparator());
    classOutput.append("import ").append(packageNameImpl).append(".data.game.world.block.Block;")
            .append(System.lineSeparator());


    classOutput.append("/**").append(System.lineSeparator()).append(" * Automatically created from mojangs reports")
            .append(System.lineSeparator()).append("*/");
    classOutput.append("public class Blocks {");

    methodOutput.append("package " + packageName + ".data.game.world.block;").append(System.lineSeparator());
    methodOutput.append("import " + packageName + ".data.game.world.block.property.*;").append(System.lineSeparator());
    methodOutput.append("import " + packageNameImpl + ".data.game.world.block.property.*;")
            .append(System.lineSeparator());
    methodOutput.append("import " + packageNameImpl + ".data.game.world.block.Block;").append(System.lineSeparator());
    methodOutput.append("import " + packageName + ".data.game.world.block.Blocks;").append(System.lineSeparator());

    methodOutput.append("/**").append(System.lineSeparator()).append(" * Automatically created from mojangs reports")
            .append(System.lineSeparator()).append("*/");
    methodOutput.append("public class BlockDef {");

    int maxID = 0;

    JsonParser jsonParser = new JsonParser();
    try (FileReader reader = new FileReader(this.blockReportFile)) {
      JsonObject blocksReport = jsonParser.parse(reader).getAsJsonObject();

      // Prepare properties (to merge custom enum classes)

      for (Map.Entry<String, JsonElement> blockE : blocksReport.entrySet()) {
        String blockName = blockE.getKey();
        JsonObject block = blockE.getValue().getAsJsonObject();

        String shortName = blockName.split(":", 2)[1].toUpperCase();

        if (block.has("properties")) {
          JsonObject properties = block.getAsJsonObject("properties");
          for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            prepareBlockProperty(shortName, property.getKey(), property.getValue().getAsJsonArray());
          }

        }
      }

      // Check
      // // propertyName -> ( Values -> blockSuffix)
      Map<String, Set<String>> names = new HashMap<>();
      for (Map<Set<String>, String> map : this.customEnumTypes.values()) {
        for (Map.Entry<Set<String>, String> x : new HashMap<>(map).entrySet()) {
          if (names.containsKey(x.getValue())) {

            if (x.getKey().containsAll(names.get(x.getValue()))) {
              // x.getKey() is the upper one?
              System.out.println("Fair warning: Expanding " + Util.reverse(x.getValue()) + " with values: " + names
                      .get(x.getValue()) + " to " + x.getKey());

              // avoid hash desync
              Set<String> tmp = names.remove(x.getValue());
              Set<String> copy = new HashSet<>(tmp);
              tmp.addAll(x.getKey());
              names.put(x.getValue(), tmp);
              customRedirects.put(copy, tmp);
              System.out.println("done!");
              continue;
            } else if (names.get(x.getValue()).containsAll(x.getKey())) {
              // x.getKey() is the upper one?
              System.out.println("Fair warning: Expanding " + Util.reverse(x.getValue()) + " with values: " + x
                      .getKey() + " to " + names.get(x.getValue()));
//                            x.getKey().addAll(names.get(x.getValue()));
              // avoid hash desync
              String tmp = map.remove(x.getKey());
              Set<String> copy = new HashSet<>(x.getKey());
              x.getKey().addAll(names.get(x.getValue()));
              Set<String> newKey = x.getKey();
              map.put(newKey, tmp);
              System.out.println("Saved as " + tmp);
              customRedirects.put(copy, newKey);
              System.out.println("done!!!");
              continue;
            }

            throw new IllegalStateException(
                    "Duplicate enum name: " + Util.reverse(x.getValue()) + " with values: " + x.getKey() + "/" + names
                            .get(x.getValue()));
          }
          names.put(x.getValue(), x.getKey());
        }
      }
      System.out.println("All checks passed - generating block data");

      // Generate

      StringBuilder stateMethodOutput;


      for (Map.Entry<String, JsonElement> blockE : blocksReport.entrySet()) {
        stateMethodOutput = new StringBuilder();

        String blockName = blockE.getKey();
        JsonObject block = blockE.getValue().getAsJsonObject();

        String shortName = blockName.split(":", 2)[1].toUpperCase();

        classOutput.append("// Generated ").append(blockName).append(System.lineSeparator());


        // Write the block properties source
        if (block.has("properties")) {
          JsonObject properties = block.getAsJsonObject("properties");
          for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            generateProperty(classOutput, shortName, property.getKey(), property.getValue().getAsJsonArray());
          }

        }

        // Write the public final static Block

        classOutput
                .append("public final static Block ")
                .append(shortName)
                .append(" = get").append(Util.toCamel(shortName)).append("();")
                .append(System.lineSeparator());


        JsonArray states = block.getAsJsonArray("states");

        stateMethodOutput
                .append("static Block get")
                .append(Util.toCamel(shortName))
                .append("(){").append(System.lineSeparator());

        String sepeareClassName = null;
        if (states.size() > 20) {
          sepeareClassName = "Block" + Util.toCamel(shortName) + "Def";
          methodOutput
                  .append("static Block get")
                  .append(Util.toCamel(shortName))
                  .append("(){").append(System.lineSeparator())
                  .append(" return " + packageName + ".data.game.world.block.")
                  .append(sepeareClassName)
                  .append(".get")
                  .append(Util.toCamel(shortName))
                  .append("();}")
                  .append(System.lineSeparator());
        }

//                if (states.size() > 150) {
//                    //TODO: BAAD
//                    System.err.println("Skipping " + shortName + "============" + states.size());
//                    System.err.println("==");
//
//                    stateMethodOutput.append(" return null; } ");
//                    continue;
//                }

        int next_method;

        stateMethodOutput.append("/* Total: ").append(states.size()).append("*/ ");

        stateMethodOutput.append(" return ");

        int total_n = -1;
        final int max_per_fnc = 30;
        int brackets_to_close = 0;
        for (int methodNr = 0,
             statesLeft = states.size();
             statesLeft > max_per_fnc;
             statesLeft -= max_per_fnc) {
          stateMethodOutput.append("/* left " + statesLeft + " bef reducing by " + max_per_fnc + " */");
          stateMethodOutput.append("con_get")
                  .append(methodNr++)
                  .append(Util.toCamel(shortName))
                  .append("( ");
          total_n = methodNr;
          brackets_to_close++;
        }

        stateMethodOutput.append(" Block.of(\"").append(blockName).append("\")");


        next_method = max_per_fnc - 0;

//
//                if (states.size() < 555) {
//                    next_method = -200;
//                    stateMethodOutput
//                            .append(" return Block.of(\"").append(blockName).append("\")")
//                            .append(System.lineSeparator());
//                } else {
//                    // Blocks like redstone_wire may cause too big methods
//                    stateMethodOutput.append(" return con_get")
//                            .append(Util.toCamel(shortName))
//                            .append(" ( Block.of(\"").append(blockName).append("\")")
//                            .append(System.lineSeparator());
//                }
        // Generate all states

        int totalStatesSoFar = 0;
        for (JsonElement stateE : states) {

          if (next_method-- == 0) {
            stateMethodOutput.append("/* Total so far: ").append(totalStatesSoFar).append("*/ ");
            if (brackets_to_close > 0) {
              for (int i = 0; i < brackets_to_close; i++) {
                stateMethodOutput.append(")");
              }
              stateMethodOutput.append(".build() /* end of main method */");
              // The closing bracket: see below
              brackets_to_close = 0;
            }

            stateMethodOutput.append(";}"); // End getX
            stateMethodOutput
                    .append("private static Block.Builder con_get")
                    .append(--total_n)
                    .append(Util.toCamel(shortName))
                    .append("(Block.Builder b){ return b").append(System.lineSeparator());
            next_method = max_per_fnc - 1;

          }
          totalStatesSoFar++;

          JsonObject state = stateE.getAsJsonObject();
          if (state.has("default") && state.getAsJsonPrimitive("default").getAsBoolean()) {
            stateMethodOutput.append(".setDefaultState(");
          } else {
            stateMethodOutput.append(".addState(");
          }
          // The most important part: The ID
          int id = state.getAsJsonPrimitive("id").getAsInt();
          stateMethodOutput.append(id).append(")");
          maxID = Math.max(maxID, id);


          // And handle the optional properties
          if (state.has("properties")) {
            JsonObject properties = state.getAsJsonObject("properties");

            for (Map.Entry<String, JsonElement> stateProperty : properties.entrySet()) {
              stateMethodOutput.append(".with( Blocks.").append(shortName).append("_")
                      .append(stateProperty.getKey().toUpperCase())
                      .append(", \"").append(stateProperty.getValue().getAsString()).append("\")");
            }
          }

          stateMethodOutput.append(".build()").append(System.lineSeparator());

        }

        if (total_n == -1) {
          stateMethodOutput.append(".build() /* end of block type */");
        }

        stateMethodOutput.append(" ;}");

        stateMethodOutput.append(System.lineSeparator()).append(System.lineSeparator());

        if (sepeareClassName == null) {
          methodOutput.append(stateMethodOutput);
        } else {
          StringBuilder sb = new StringBuilder();

          sb.append("package " + packageName + ".data.game.world.block;").append(System.lineSeparator());
          sb.append("import " + packageName + ".data.game.world.block.property.*;").append(System.lineSeparator());
          sb.append("import " + packageNameImpl + ".data.game.world.block.Block;").append(System.lineSeparator());
          sb.append("import " + packageName + ".data.game.world.block.Blocks;").append(System.lineSeparator());

          sb.append("/**").append(System.lineSeparator()).append(" * Automatically created from mojangs reports")
                  .append(System.lineSeparator()).append("*/");
          sb.append("public class ");
          sb.append(sepeareClassName);
          sb.append(" { ");
          sb.append(System.lineSeparator());
          sb.append(stateMethodOutput);
          sb.append("}");

          Util.writeFile(new File(sourceDirectory,
                                  packageName
                                          .replace('.', '/') + "/data/game/world/block/" + sepeareClassName + ".java"),
                         sb.toString());
          System.out.println(sepeareClassName);
          Util.writeFile(new File(sourceDirectory,
                                  packageName
                                          .replace('.', '/') + "/data/game/world/block/" + sepeareClassName + ".java"),
                         formatter.formatSource(sb.toString()));


        }
      }


    }

    classOutput.append("public static final int MAX_BLOCK_STATE_ID = ").append(maxID).append(";");

    classOutput.append("}");
    // Write the formatted source to Blocks.java
    Util.writeFile(new File(sourceDirectory, packageName.replace('.', '/') + "/data/game/world/block/Blocks.java"),
                   formatter.formatSource(classOutput.toString()));


    methodOutput.append("}");
    // Write the formatted source to BlockDef.java

    Util.writeFile(new File(sourceDirectory, packageName.replace('.', '/') + "/data/game/world/block/BlockDef.java"),
                   methodOutput.toString());

    // Write the formated again
    Util.writeFile(new File(sourceDirectory, packageName.replace('.', '/') + "/data/game/world/block/BlockDef.java"),
                   formatter.formatSource(methodOutput.toString()));
    System.out.println(
            "Generated all Blocks and BlockStates with the heighest id being " + maxID + " (don't forget to +1)");
  }

  // propertyName -> ( Values -> blockSuffix)
  private Map<String, Map<Set<String>, String>> customEnumTypes = new HashMap<>();

  // map for redirecting after expanding checks
  // ToDo: Map to property name
  private Map<Set<String>, Set<String>> customRedirects = new HashMap<>();

  // A list to keep track of enum files we have already generated
  private List<String> generatedEnums = new ArrayList<>();

  // A formatter for readable code
  private com.google.googlejavaformat.java.Formatter formatter = new com.google.googlejavaformat.java.Formatter();

  private void prepareBlockProperty(String blockName, String name, JsonArray jsonValues) {

    String bn = new StringBuilder().append(blockName).reverse().toString();


    Set<String> values = StreamSupport.stream(jsonValues.spliterator(), false)
            .map(JsonElement::getAsString).collect(Collectors.toSet());

    // Manual overrides
    bn = ManualOverrides.customRenaming(bn, name, values);


    if (!(values.size() == 2 && values.contains("true") && values.contains("false"))
            && !Util.isIntList(values)
            && !(Util.isListOf(values, "x", "y", "z") && !values.contains("side")) // We use a shortcut for Axis
            && !(Util.isListOf(values, "down", "up", "north", "south", "west", "east", "special") && !values
            .contains("side"))) {
      // We have to create a custom enum for this property
      // (it is neither a Boolean, Int, Axis or BlockFace

      // In the following we will check if we can merge those enums for multiple blocks


      Map<Set<String>, String> otherEnums = customEnumTypes.computeIfAbsent(name, s -> new HashMap<>());
      String other = otherEnums.get(values);
      if (other != null) {
        // It looks like another block also requires that property
        // Here we get the longest common suffix (reverse prefix)
        // e.g. WHITE_BED and BLACK_BED will result in BED
        String pref = Util.greatestCommonPrefix(other, bn).replace("_", "");

        if (!pref.isEmpty()) {
          otherEnums.remove(values);

          otherEnums.put(values, pref);
        } else {
          // We were unable to find the common name of these items
          // Check ManualOverrides
          throw new IllegalStateException(
                  "Can't handle custom enum creation for " + values.toString() + " and property name " + name + "//"
                          + blockName + "<->" + new StringBuilder(other).reverse().toString());
        }
      } else {
        otherEnums.put(values, bn);
      }
    }
  }

  private void generateProperty(StringBuilder out, String blockName, String name, JsonArray jsonValues)
          throws FormatterException, IOException {

    Set<String> values = StreamSupport.stream(jsonValues.spliterator(), false)
            .map(JsonElement::getAsString).collect(Collectors.toSet());
    if (values.size() == 2 && values.contains("true") && values.contains("false")) {
      // Generate boolean property
      out.append("public final static BlockPropertyBoolean ")
              .append(blockName).append("_").append(name.toUpperCase())
              .append(" = BlockPropertyBoolean.create(\"").append(name).append("\");")
              .append(System.lineSeparator());
    } else if (Util.isIntList(values)) {
      // Generate int property
      String varName = blockName + "_" + name.toUpperCase();

      out.append("public final static BlockPropertyInteger ")
              .append(varName)
              .append(" = BlockPropertyInteger.create(\"").append(name).append("\" ");
      for (String x : values) {
        out.append(", Integer.valueOf(\"").append(x).append("\") ");
      }
      out.append(");")
              .append(System.lineSeparator());
    } else if (Util.isListOf(values, "x", "y", "z") && !values.contains("side")) {
      // Generate Axis enum property
      String varName = blockName + "_" + name.toUpperCase();
      writeEnumProperty(out, varName, name, packageNameImpl + ".data.game.world.Axis", values);
    } else if (Util.isListOf(values, "down", "up", "north", "south", "west", "east", "special") && !values
            .contains("side")) {
      // Generate BlockFace enum property
      String varName = blockName + "_" + name.toUpperCase();
      writeEnumProperty(out, varName, name, packageNameImpl + ".data.game.world.block.property.BlockFace",
                        values);
    } else {
      // Handle custom enum property


      Map<Set<String>, String> otherEnums = customEnumTypes.computeIfAbsent(name, s -> new HashMap<>());

      String currentSuffix = otherEnums.get(values);

      while (currentSuffix == null) {
        System.err.println(blockName + values);
        values = customRedirects.get(values);
        if (values == null) {
          throw new IllegalStateException("An redirect is missing?");
        }
        currentSuffix = otherEnums.get(values);
        System.err.println(blockName + values);
      }
      // A more java-like classname
      String clazzName = Util.toCamel(Util.reverse(currentSuffix) + "_" + name.toUpperCase());

      String fullClazz = "" + packageName + ".data.game.world.block.property.enums." + clazzName;
      String varName = blockName + "_" + name.toUpperCase();

      // generate the property
      writeEnumProperty(out, varName, name, fullClazz, values);

      if (generatedEnums.contains(clazzName)) {
        return;
      }

      generatedEnums.add(clazzName);

      // Generate the enum class
      Util.writeFile(new File(sourceDirectory, Util.packageToPath(fullClazz) + ".java"),
                     writeEnumFile(clazzName, values));
    }
  }

  private void writeEnumProperty(StringBuilder out, String variableName, String propertyName, String enumClass,
                                 Collection<String> values) {
    out.append("public final static BlockPropertyEnum<").append(enumClass).append("> ")
            .append(variableName)
            .append(" = BlockPropertyEnum.create(\"").append(propertyName).append("\", ").append(enumClass)
            .append(".class");
    for (String x : values) {
      out.append(", ").append(enumClass).append(".valueOf(\"").append(x.toUpperCase()).append("\") ");
    }
    out.append(");")
            .append(System.lineSeparator());
  }

  /**
   * Generate a custom enum class for that property
   *
   * @param clazzName the name of the class
   * @param values    the possible enum values
   * @return the formatted java source
   * @throws FormatterException in case stuff went wrong
   */
  private String writeEnumFile(String clazzName, Collection<String> values) throws FormatterException {
    StringBuilder ret = new StringBuilder();
    ret.append("package " + packageName + ".data.game.world.block.property.enums;").append(System.lineSeparator());
    ret.append("public enum ").append(clazzName)
            .append(" implements " + packageNameImpl + ".data.game.world.block.property.IBlockPropertyEnum {")
            .append(System.lineSeparator());

    StringJoiner joiner = new StringJoiner(",");
    // Make a set so enum values are unique (after possible epanding)
    // ToDo: Move to expanding (the unique making)
    new HashSet<>(values).stream().map(String::toUpperCase).forEach(joiner::add);
    ret.append(joiner.toString()).append(";").append(System.lineSeparator());
    ret.append("@Override").append(System.lineSeparator())
            .append("public String getName() {  return this.name().toLowerCase();     }").append(System.lineSeparator())
            .append("}");

    return formatter.formatSource(ret.toString());
  }


}
