import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import org.apache.commons.io.FilenameUtils;

/**
 * JavaMethodParserのエントリーポイント用クラス
 */
public class Main {
    final static int[] FILE_NUM = { 0 };
    final static long[] totalResolveSuccess = { 0 };
    final static long[] totalResolveFailed = { 0 };
    final static int[] resolveSuccess = { 0 };
    final static int[] resolveFailed = { 0 };

    public static void main(String[] args) throws IOException, InterruptedException {
        Path dataRoot = Paths.get("data").toAbsolutePath().normalize();
        Path rawDataDir = dataRoot.resolve("processed_data");
        Path inputDir = dataRoot.resolve("relations");

        /*
         * 異常に処理に時間がかかるプロジェクトがあった場合，一旦処理を中止してそのプロジェクトを除外してから，途中から処理を再開できるようにするためのファイル
         */
        Path tmp = dataRoot.resolve("tmp");
        List<String> ignoredProjectNamesWithSubsetNames = Files.readAllLines(tmp);

        Random rand = new Random(0L);
        rand.nextInt(5);

        long start = System.nanoTime();

        try (Stream<Path> rawRoleDirs = Files.list(rawDataDir)) {
            rawRoleDirs.forEach(rawRoleDir -> {
                Path roleName = rawRoleDir.getFileName();
                Path destDirAbsPath = inputDir.resolve(roleName);

                try (Stream<Path> projectStream = Files.list(rawRoleDir).filter(Files::isDirectory)) {
                    projectStream.forEach(projectRootDir -> {
                        resolveSuccess[0] = 0;
                        resolveFailed[0] = 0;
                        String projectName = projectRootDir.getFileName().toString();
                        System.out.println("\n\n-----\n");
                        System.out.println(projectRootDir.getFileName());

                        if (ignoredProjectNamesWithSubsetNames.contains(projectName)) {
                            return;
                        }

                        // java-med, java-large, java-large, java-large, java-large, java-large,
                        // java-large, java-large
                        List<String> ignoredProjectNames1 = List.of("apache__hive",
                                "GoogleCloudPlatform__google-cloud-java",
                                "clementine-player__Android-Remote", "oVirt__ovirt-engine", "VUE__VUE", "palatable__lambda",
                                "amutu__tdw",
                                "usethesource__capsule");

                        if (ignoredProjectNames1.contains(projectName)) {
                            return;
                        }

                        /*
                         * Solverの準備
                         */
                        TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
                        TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(
                                projectRootDir.toAbsolutePath().toFile());
                        // TypeSolver platform_framework_base = new JavaParserTypeSolver(
                        // new File(rawDataDir.getAbsolutePath() + "/platformframeworksbase"));
                        reflectionTypeSolver.setParent(reflectionTypeSolver);
                        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
                        combinedSolver.add(reflectionTypeSolver);
                        combinedSolver.add(javaParserTypeSolver);
                        // combinedSolver.add(platform_framework_base);
                        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
                        JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);

                        // String out = cmd.getOptionValue("outputdir");

                        /*
                         * 取得元ディレクトリからJavaファイルを再帰的に探索
                         */
                        try (Stream<Path> javaFileStream = Files.walk(projectRootDir)
                                .filter(path -> Files.isRegularFile(path)
                                        && FilenameUtils.getExtension(path.toAbsolutePath().toString())
                                                .equals("java"))) {
                            javaFileStream.forEach(javaFile -> {
                                FILE_NUM[0] += 1;

                                Path javaFileRelPath = rawRoleDir.relativize(javaFile);
                                Path jsonFileRelPath = javaFileRelPath
                                        .resolveSibling(javaFileRelPath.getFileName() + ".json");

                                System.out.print("\r");
                                System.out.print("Parsing : " + javaFile.getFileName());

                                try {
                                    CompilationUnit cu = JavaParser.parse(javaFile);

                                    Resolver resolver = new Resolver(true);
                                    resolver.execute(cu);

                                    resolveSuccess[0] += resolver.getResolveSuccess();
                                    resolveFailed[0] += resolver.getResolveFailed();

                                    makeJsons(resolver, destDirAbsPath, jsonFileRelPath);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }

                        try {
                            Files.write(tmp, List.of(projectName), StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        totalResolveSuccess[0] += resolveSuccess[0];
                        totalResolveFailed[0] += resolveFailed[0];
                        System.out.println();
                        System.out.println("resolveSuccess : " + resolveSuccess[0]);
                        System.out.println("resolveFailed : " + resolveFailed[0]);
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        }

        long time = System.nanoTime() - start;
        int hour = (int) (time / 3600000000000L);
        int minute = (int) ((time - (long) hour * 3600000000000L) / 60000000000L);
        int second = (int) ((time - (long) hour * 3600000000000L) - (long) minute * 60000000000L / 1000000000L);
        System.out.println("parse & resolve time : " + hour + "時間" + minute + "分" + second + "秒");
        System.out.println("totalResolveSuccess : " + totalResolveSuccess[0]);
        System.out.println("totalResolveFailed : " + totalResolveFailed[0]);
        System.out.println("FILE_NUM : " + FILE_NUM[0]);
    }

    private static List<Path> makeJsons(Resolver resolver, Path destDirAbsPath, Path jsonRelPath)
            throws IOException {
        List<Path> jsonFiles = new ArrayList<>();
        for (Entry<String, HashMap<String, ArrayList<String>>> relation : resolver.getRelations().entrySet()) {
            JsonGenerator jsonGenerator = new JsonGenerator(relation.getValue(), true);
            Path jsonFile = destDirAbsPath.resolve(relation.getKey()).resolve(jsonRelPath);
            jsonGenerator.saveFile(jsonFile.toString());
            jsonFiles.add(jsonFile);
        }
        return jsonFiles;
    }
}
