import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import org.apache.commons.io.FilenameUtils;

public class FilePathOrganizer {
    public static void main(String[] args) throws IOException {
        Path dataRoot = Paths.get("data").toAbsolutePath().normalize();
        Path rawDataDir = dataRoot.resolve("raw");
        Path processedDataDir = dataRoot.resolve("processed_data");

        try (Stream<Path> roleDirs = Files.list(rawDataDir)) {
            roleDirs.forEach(roleDir -> {
                try (Stream<Path> projectRootDirs = Files.list(roleDir).filter(Files::isDirectory)) {
                    projectRootDirs.forEach(projectRootDir -> {
                        try (Stream<Path> javaFileStream = Files.walk(projectRootDir)
                                .filter(path -> Files.isRegularFile(path)
                                        && FilenameUtils.getExtension(path.toAbsolutePath().toString())
                                                .equals("java"))) {
                            javaFileStream.forEach(javaFile -> {
                                try {
                                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                                    String filePathInPackage;
                                    if (cu.getPackageDeclaration().isPresent()) {
                                        filePathInPackage = cu.getPackageDeclaration().get().getNameAsString().replace(
                                                ".",
                                                "/");
                                    } else {
                                        System.out.println(
                                                "\n" + javaFile.getFileName().toString()
                                                        + "Since there is no package declaration, it is directly put under the root directory");
                                        filePathInPackage = "";
                                    }
                                    String javaFileName = javaFile.getFileName().toString();
                                    Path directories = processedDataDir.resolve(roleDir.getFileName())
                                            .resolve(projectRootDir.getFileName())
                                            .resolve(filePathInPackage);
                                    System.out.print("\r" + directories.toAbsolutePath() + javaFileName);

                                    Files.createDirectories(directories);
                                    Path from = javaFile.toAbsolutePath();
                                    Path to = directories.resolve(javaFileName);
                                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                                } catch (ParseProblemException p) {
                                    System.out.println(p);
                                } catch (StackOverflowError e) {
                                    System.err.println(e);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

    }
}
