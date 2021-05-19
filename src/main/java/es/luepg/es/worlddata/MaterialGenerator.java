package es.luepg.es.worlddata;

import com.google.googlejavaformat.java.FormatterException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Maven plugin that generates the Materials from minecraft reports
 *
 * @author MisterErwin
 */
@Mojo(name = "genmaterials", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)

public class MaterialGenerator extends AbstractMojo {

  @Parameter(property = "gen.targetDirectory", defaultValue = "${project.basedir}/target/generated-sources", required = true)
  private File sourceDirectory;

  @Parameter(property = "gen.report.registries", defaultValue = "${project.basedir}/src/main/resources/reports/registries.json", required = true)
  private File registriesReportFile;

  @Parameter(defaultValue = "${project}")
  private org.apache.maven.project.MavenProject project;

  private String packageName = "es.luepg.mcdata";
  private String packageNameImpl = "es.luepg.ecs";


  private com.google.googlejavaformat.java.Formatter formatter = new com.google.googlejavaformat.java.Formatter();

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

    private void doGenerate() throws IOException, FormatterException, URISyntaxException {
        System.out.println("Using registry reports from " + registriesReportFile.getAbsolutePath());
        System.out.println("Outputting source to " + sourceDirectory.getAbsolutePath());
        project.addCompileSourceRoot(sourceDirectory.getPath());

        Map<String, MaterialData> materials = new HashMap<>();

        JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader(this.registriesReportFile)) {
            JsonObject registries = jsonParser.parse(reader).getAsJsonObject();

            JsonObject blockRegistry = registries.getAsJsonObject("minecraft:block")
                    .getAsJsonObject("entries");
            JsonObject itemRegistry = registries.getAsJsonObject("minecraft:item")
                    .getAsJsonObject("entries");

            for (Map.Entry<String, JsonElement> e : blockRegistry.entrySet()) {

                MaterialData data = materials.computeIfAbsent(e.getKey(), s -> new MaterialData());

                data.block_id = e.getValue().getAsJsonObject()
                        .getAsJsonPrimitive("protocol_id").getAsInt();

            }

            for (Map.Entry<String, JsonElement> e : itemRegistry.entrySet()) {

                MaterialData data = materials.computeIfAbsent(e.getKey(), s -> new MaterialData());

                data.item_id = e.getValue().getAsJsonObject()
                        .getAsJsonPrimitive("protocol_id").getAsInt();

            }


        }

        // Generate
        StringBuilder enumData = new StringBuilder();

        for (Map.Entry<String, MaterialData> dataEntry : materials.entrySet()) {
            String shortName = dataEntry.getKey().split(":", 2)[1].toUpperCase();

//            String camelName = Util.toCamel(shortName);

            enumData.append(shortName)
                    .append("(\"")
                    .append(dataEntry.getKey())
                    .append("\", ")
                    .append(dataEntry.getValue().block_id)
                    .append(",")
                    .append(dataEntry.getValue().item_id)
                    .append(")")
                    .append(System.lineSeparator());

            if (dataEntry.getValue().block_id != -1) {

              enumData.append(" { @Nullable @Override ")
                      .append("public " + packageNameImpl + ".data.game.world.block.Block getBlock() {");

                enumData.append(" return Blocks.")
                .append(shortName)
                .append(";");

                enumData.append("} }");
            }
          enumData.append(",").append(System.lineSeparator());
        }


      List<String> matLines = Files.readAllLines(

              Util.getResource(getClass().getClassLoader(), "es/luepg/worlddata/Material.template")
              , Charset.defaultCharset());

      matLines = matLines.stream().map(x -> x.replace("%BLOCK_PACKAGE_NAME%", packageNameImpl))
              .collect(Collectors.toList());
      matLines = matLines.stream().map(x -> x.replace("%PACKAGE_NAME%", packageName)).collect(Collectors.toList());

      boolean replaced = false;
      StringBuilder matFile = new StringBuilder();
      for (String l : matLines) {
        if (replaced) {
          matFile.append(l);
        } else if (l.contains("%GENERATION_TARGET%")) {
          replaced = true;
          matFile.append(enumData);
        } else {
                matFile.append(l);
            }
            matFile.append(System.lineSeparator());

        }
      Util.writeFile(new File(sourceDirectory, packageName.replace('.', '/') + "/data/game/world/Material.java"),
                     matFile.toString());

      Util.writeFile(new File(sourceDirectory, packageName.replace('.', '/') + "/data/game/world/Material.java"),
                     formatter.formatSource(matFile.toString()));

        System.out.println("Generated Material.java");
    }


    static class MaterialData {
        int block_id = -1;
        int item_id = -1;
    }

}
