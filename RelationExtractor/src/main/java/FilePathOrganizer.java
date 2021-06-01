import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;

import org.apache.commons.io.FilenameUtils;

public class FilePathOrganizer {
    public static void main(String[] args) {
        String dataName = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--data")) {
                dataName = args[++i];
            }
        }

        File dataRoot = Paths.get("data").toAbsolutePath().normalize().toFile();
        File evaldataDir = Paths.get(dataRoot.getAbsolutePath(), "evaldata", dataName).toAbsolutePath().normalize()
                .toFile();
        String processedDataDirPath = Paths.get(dataRoot.getAbsolutePath(), "processed_data", dataName).toAbsolutePath()
                .normalize().toString();

        for (File subsetDir : evaldataDir.listFiles()) {
            for (File projectRootDir : subsetDir.listFiles()) {
                if (!projectRootDir.isDirectory())
                    continue;
                String projectName = formatDirName(getPrefix(projectRootDir.getName()));
                if (projectName.equals("eclipseceylon"))
                    continue;

                ArrayList<File> javaFiles = getJavaFilesRecursively(projectRootDir);
                try {
                    javaFiles.forEach(javaFile -> {
                        try {
                            CompilationUnit cu = JavaParser.parse(javaFile);
                            String filePathInPackage;
                            if (cu.getPackageDeclaration().isPresent()) {
                                filePathInPackage = cu.getPackageDeclaration().get().getNameAsString().replace(".",
                                        "/");
                            } else {
                                System.out.println("\n" + javaFile.getName() + "Since there is no package declaration, "
                                        + "it is directly put under the root directory");
                                filePathInPackage = "";
                            }
                            String javaFileName = javaFile.getName();
                            Path directories = Paths.get(processedDataDirPath, subsetDir.getName(), projectName,
                                    filePathInPackage);
                            System.out.print("\r" + directories.toAbsolutePath() + javaFileName);
                            try {
                                Files.createDirectories(directories);
                            } catch (FileAlreadyExistsException al) {
                                System.out.println(al);
                            } catch (IOException e) {
                                System.out.println("Directory creation failed\n" + e);
                            }
                            Path from = Paths.get(javaFile.getAbsolutePath());
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
                } catch (NoSuchElementException e) {
                    System.out.println(e);
                }
            }
        }

    }

    /**
     * Recursively search for Java files contained inside the directories
     * 
     * @param dir A File object representing the directory to be searched
     * @return An ArrayList of File objects representing the Java files contained
     *         within
     */
    private static ArrayList<File> getJavaFilesRecursively(File dir) {
        ArrayList<File> fileList = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null)
            return new ArrayList<>();

        for (File file : files) {
            if (!file.exists()) {
                continue;
            } else if (file.isDirectory()) {
                fileList.addAll(getJavaFilesRecursively(file));
            } else if (file.isFile() && FilenameUtils.getExtension(file.getAbsolutePath()).equals("java")) {
                fileList.add(file);
            }
        }
        return fileList;
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
