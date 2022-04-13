import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;

import org.apache.commons.io.FilenameUtils;

public class FilePathOrganizer {
    public static void main(String[] args) throws IOException {
        Path dataRoot = Paths.get("data").toAbsolutePath().normalize();
        Path rawDataDir = Paths.get(dataRoot.toAbsolutePath().toString(), "raw").toAbsolutePath().normalize();
        String processedDataDirPath = Paths.get(dataRoot.toAbsolutePath().toString(), "processed_data").toAbsolutePath()
                .normalize().toString();

        try (Stream<Path> roleDirs = Files.list(rawDataDir)) {
            roleDirs.forEach(roleDir -> {
                try (Stream<Path> projectRootDirs = Files.list(roleDir).filter(Files::isDirectory)) {
                    projectRootDirs.forEach(projectRootDir -> {
                        String projectName = formatDirName(getPrefix(projectRootDir.getFileName().toString()));
                        if (projectName.equals("eclipseceylon"))
                            return;

                        try (Stream<Path> javaFileStream = Files.walk(projectRootDir)
                                .filter(path -> Files.isRegularFile(path)
                                        && FilenameUtils.getExtension(path.toAbsolutePath().toString())
                                                .equals("java"))) {
                            javaFileStream.forEach(javaFile -> {
                                try {
                                    CompilationUnit cu = JavaParser.parse(javaFile);
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
                                    Path directories = Paths.get(processedDataDirPath,
                                            roleDir.getFileName().toString(),
                                            projectName,
                                            filePathInPackage);
                                    System.out.print("\r" + directories.toAbsolutePath() + javaFileName);
                                    try {
                                        Files.createDirectories(directories);
                                    } catch (FileAlreadyExistsException al) {
                                        System.out.println(al);
                                    } catch (IOException e) {
                                        System.out.println("Directory creation failed\n" + e);
                                    }
                                    Path from = javaFile.toAbsolutePath();
                                    Path to = Paths.get(directories.toString(), javaFileName);
                                    try {
                                        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                                    } catch (Exception e) {
                                        System.out.println("Copy failed\n" + e.fillInStackTrace());
                                    }
                                } catch (ParseProblemException p) {
                                    System.out.println(p);
                                } catch (Exception e) {
                                    System.out.println(e);
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

    private static String formatDirName(String dirName) {
        return dirName.replace("-", "").replace("_", "");
    }

    private static String getPrefix(String fileName) {
        if (fileName == null)
            return null;
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            fileName = fileName.substring(0, point);
        }
        point = fileName.lastIndexOf("/");
        if (point != -1) {
            return fileName.substring(point + 1);
        }
        return fileName;
    }
}
