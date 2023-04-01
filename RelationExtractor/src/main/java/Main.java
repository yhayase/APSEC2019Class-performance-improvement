import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.opencsv.CSVWriter;

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

    static boolean useSourceRoot = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        Path dataRoot = Paths.get("data").toAbsolutePath().normalize();
        Path rawDataDir = dataRoot.resolve("processed_data");

        Path inputDir = dataRoot.resolve("relations");
        Files.createDirectory(inputDir);

        Random rand = new Random(0L);
        rand.nextInt(5);

        long start = System.nanoTime();

        try (Stream<Path> rawRoleDirs = Files.list(rawDataDir)) {
            rawRoleDirs.forEach(rawRoleDir -> {
                Path roleName = rawRoleDir.getFileName();
                try {
                    Files.createDirectory(inputDir.resolve(roleName));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                try (Stream<Path> projectStream = Files.list(rawRoleDir).filter(Files::isDirectory)) {
                    projectStream.forEach(projectRootDir -> {
                        resolveSuccess[0] = 0;
                        resolveFailed[0] = 0;
                        String projectName = projectRootDir.getFileName().toString();
                        System.out.println("\n\n-----\n");
                        System.out.println(projectRootDir.getFileName());

                        Path csvPath = inputDir.resolve(roleName).resolve(projectName + ".csv");

                        // java-med
                        List<String> ignoredProjectNames1 = List.of("apache__hive");

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
                        //reflectionTypeSolver.setParent(reflectionTypeSolver);
                        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
                        combinedSolver.add(reflectionTypeSolver);
                        combinedSolver.add(javaParserTypeSolver);
                        // combinedSolver.add(platform_framework_base);
                        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);

                        if (useSourceRoot) {
                            try (CSVWriter csvWriter = new CSVWriter(new FileWriter(csvPath.toFile()))) {
                                SourceRoot sourceRoot = new SourceRoot(projectRootDir);
                                sourceRoot.getParserConfiguration().setSymbolResolver(symbolSolver);
                                var resultList = sourceRoot.tryToParseParallelized();

                                for (var r : resultList) {
                                    r.getResult().ifPresent(cu -> {
                                        System.out.print(cu.getStorage().get().getPath().getFileName() + " \r");
                                        Resolver resolver = new Resolver(true);
                                        resolver.execute(cu);
                                        resolveSuccess[0] += resolver.getResolveSuccess();
                                        resolveFailed[0] += resolver.getResolveFailed();

                                        for (GraphEdge edge : resolver.getEdges()) {
                                            String[] line = { edge.source().type(),
                                                    edge.source().name(), edge.type(), edge.target().type(),
                                                    edge.target().name() };
                                            csvWriter.writeNext(line);
                                        }
                                    });
                                }
                            } catch (ParseProblemException e) {
                                System.out.println("ParseProblemException : " + e.getMessage());
                                return; // continue to next file
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            } finally {
                                JavaParserFacade.clearInstances();
                                System.gc();
                            }

                        } else {
                            StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);

                            /*
                            * 取得元ディレクトリからJavaファイルを再帰的に探索
                            */
                            try (Stream<Path> javaFileStream = Files.walk(projectRootDir)
                                    .filter(path -> Files.isRegularFile(path)
                                            && FilenameUtils.getExtension(path.toAbsolutePath().toString())
                                                    .equals("java"));
                                    CSVWriter csvWriter = new CSVWriter(new FileWriter(csvPath.toFile()))) {

                                javaFileStream.forEach(javaFile -> {
                                    FILE_NUM[0] += 1;

                                    System.out.print("\r");
                                    System.out.print("Parsing : " + javaFile.getFileName());

                                    Resolver resolver = new Resolver(true);
                                    try {
                                        CompilationUnit cu = StaticJavaParser.parse(javaFile);
                                        resolver.execute(cu);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    } catch (ParseProblemException e) {
                                        System.out.println("ParseProblemException : " + javaFile.getFileName() + " : "
                                                + e.getMessage());
                                        return; // continue to next file
                                    }
                                    resolveSuccess[0] += resolver.getResolveSuccess();
                                    resolveFailed[0] += resolver.getResolveFailed();

                                    for (GraphEdge edge : resolver.getEdges()) {
                                        String[] line = { edge.source().type(),
                                                edge.source().name(), edge.type(), edge.target().type(),
                                                edge.target().name() };
                                        csvWriter.writeNext(line);
                                    }
                                });
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            } finally {
                                JavaParserFacade.clearInstances();
                                System.gc();
                            }
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

        var duration = Duration.ofNanos(System.nanoTime() - start);
        System.out.printf("parse & resolve time : %d:%02d:%02d\n", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
        System.out.println("totalResolveSuccess : " + totalResolveSuccess[0]);
        System.out.println("totalResolveFailed : " + totalResolveFailed[0]);
        System.out.println("FILE_NUM : " + FILE_NUM[0]);
    }
}
