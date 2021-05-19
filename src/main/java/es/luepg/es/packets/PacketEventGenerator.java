package es.luepg.es.packets;

import es.luepg.es.worlddata.Util;
import com.google.googlejavaformat.java.FormatterException;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Maven plugin that generates the PacketReceivedEvents
 *
 * ToDo: Move me to an own project, as this generated code is specific to our ECS server
 *
 * @author MisterErwin
 */
@Mojo(name = "genpacketwrappers", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)

public class PacketEventGenerator extends AbstractMojo {

    @Parameter(property = "gen.targetDirectory", defaultValue = "${project.basedir}/target/generated-sources", required = true)
    private File sourceDirectory;

    @Parameter(property = "protolib.artifact", defaultValue = "mcprotocollib", required = true)
    private String protocolLibArtifactId;

    @Parameter(property = "protolib.groupId", defaultValue = "com.github.steveice10", required = true)
    private String protocolLibGrouptId;

    @Parameter(property = "protolib.packetPath", defaultValue = "com.github.steveice10.mc.protocol.packet.ingame.client", required = true)
    private String packetPath;


    @Parameter(defaultValue = "${project}")
    private org.apache.maven.project.MavenProject project;

  private final com.google.googlejavaformat.java.Formatter formatter = new com.google.googlejavaformat.java.Formatter();

    public static void main(String[] a) throws Exception {
        // ToDO
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
        System.out.println("Outputting source to " + sourceDirectory.getAbsolutePath());

        project.addCompileSourceRoot(sourceDirectory.getPath());

        packetPath = packetPath.replace('.', '/');

        // Packet class - EventWrapper
        Map<String, String> allPackets = new HashMap<>();

        for (Object o : project.getDependencyArtifacts()) {
            if (o instanceof DefaultArtifact) {
                DefaultArtifact a = (DefaultArtifact) o;
                if (!a.getGroupId().equals(this.protocolLibGrouptId)
                        || !a.getArtifactId().equals(this.protocolLibArtifactId))
                    continue;


                try (ZipFile zipFile = new ZipFile(a.getFile())) {
                    Enumeration entries = zipFile.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry e = (ZipEntry) entries.nextElement();
                        if (e.getName().startsWith(packetPath)
                                && e.getName().endsWith("Packet.class")) {

                            String[] sp = e.getName().split("\\.", 2);
                            String fullPath = sp[0];
                            sp = fullPath.split("/");
                            String packetName = sp[sp.length - 1];


                            StringBuilder targetPath = new StringBuilder("es.luepg.ecs.packetwrapper.event");
                            for (int i = 6; i < sp.length - 1; i++) {
                                targetPath.append(".").append(sp[i]);
                            }

                            StringBuilder content = new StringBuilder();
                            content.append("package ").append(targetPath.toString()).append(";");
                            content.append(header);

                            targetPath.append(".").append(sp[sp.length - 1]).append("ReceivedEvent");

                            allPackets.put(String.join(".", sp), targetPath.toString());

                            content.append("public class ")
                                    .append(packetName).append("ReceivedEvent extends PlayerPacketReceivedEvent<")
                                    .append(String.join(".", sp)).append("> {")
                                    .append("public ").append(packetName).append("ReceivedEvent")
                                    .append("(Session session, ")
                                    .append(String.join(".", sp))
                                    .append(" packet, World world, Entity playerEntity, GameProfile profile) {")
                                    .append("super(session, packet, world, playerEntity, profile);")
                                    .append("}}");

                            File targetEventFile = new File(sourceDirectory, "es/luepg/ecs/packetwrapper/event");

                            for (int i = 6; i < sp.length - 1; i++) {
                                targetEventFile = new File(targetEventFile, sp[i]);
                            }
                            targetEventFile = new File(targetEventFile, packetName + "ReceivedEvent.java");
                            System.out.println(targetEventFile);

                            Util.writeFile(targetEventFile, content.toString());

                            Util.writeFile(targetEventFile, formatter.formatSource(content.toString()));


                        }
                    }

                    StringBuilder collector = new StringBuilder(helper_header);

                    for (Map.Entry<String, String> e : allPackets.entrySet()) {
                        // Packet class - EventWrapper
                        collector.append(" if ( clazz == ").append(e.getKey()).append(".class) {")
                                .append(" return new ").append(e.getValue())
                                .append("(session, (")
                                .append(e.getKey())
                                .append(") packet, world, playerEntity, profile);")
                                .append(" }else  ");
                    }
                    collector.append(helper_tail);

                    File helper = new File(sourceDirectory, "es/luepg/ecs/packetwrapper/PlayerPacketReceivedEventHelper.java");

                    Util.writeFile(helper, collector.toString());

                    Util.writeFile(helper, formatter.formatSource(collector.toString()));

                    System.out.println("Finished packets");
                    return;

                }
            }
        }
        System.err.println("Unable to find any artifact");
    }

    private final String header = "\n" +
            "import com.artemis.Entity;\n" +
            "import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerStatePacket;\n" +
            "import com.github.steveice10.packetlib.Session;\n" +
            "import com.github.steveice10.packetlib.packet.Packet;\n" +
            "import com.github.steveice10.mc.auth.data.GameProfile;\n" +
            "import es.luepg.ecs.packetwrapper.PlayerPacketReceivedEvent;\n" +
            "import es.luepg.ecs.world.World;\n" +
            "\n" +
            "/**\n" +
            " * Auto generated\n" +
            " */\n";

    private final String helper_header = "package es.luepg.ecs.packetwrapper;\n" +
            "\n" +
            "import com.artemis.Entity;\n" +
            "import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientBlockNBTRequestPacket;\n" +
            "import com.github.steveice10.packetlib.Session;\n" +
            "import com.github.steveice10.packetlib.packet.Packet;\n" +
            "import com.github.steveice10.mc.auth.data.GameProfile;\n"+
            "import es.luepg.ecs.world.World;\n" +
            "import lombok.AllArgsConstructor;\n" +
            "import lombok.Getter;\n" +
            "\n" +
            "\n" +
            "public abstract class PlayerPacketReceivedEventHelper {\n" +
            "    \n" +
            "    public static PlayerPacketReceivedEvent call(Session session, Packet packet, World world, Entity playerEntity, GameProfile profile) {\n" +
            "        Class<? extends Packet> clazz = packet.getClass();\n";

    private final String helper_tail = "{\n" +
            "            System.err.println(\"Unhandled \" + packet + \"//\" + clazz.getName());\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
}
