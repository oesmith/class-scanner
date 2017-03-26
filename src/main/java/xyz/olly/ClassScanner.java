package xyz.olly;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Optional;
import java.util.Set;

public final class ClassScanner {
  private Set<String> classNames;
  private Queue<Path> queue;

  public ClassScanner() {
    classNames = new HashSet<>();
    queue = new ArrayDeque<>();
  }

  public void addRoot(Path p) {
    queue.add(p);
  }

  public void run() {
    while (!queue.isEmpty()) {
      visit(queue.remove());
    }
  }

  public Set<String> getClassNames() {
    return classNames;
  }

  private void visit(Path p) {
    if (Files.isDirectory(p)) {
      processDirectory(p);
    } else if (p.toString().endsWith(".java")) {
      processSource(p);
    } else if (p.toString().endsWith(".jar")) {
      System.err.println(String.format("WARN: jar files unsupported [%s]", p.toString()));
    }
  }

  private void processDirectory(Path directoryPath) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
      for (Path path : stream) {
        queue.add(path);
      }
    } catch(IOException e) {
      System.err.println(String.format("ERR: reading folder %s\n%s", directoryPath.toString(), e));
    }
  }

  private void processSource(Path sourcePath) {
    try {
      new ClassVisitor().visit(JavaParser.parse(sourcePath), classNames);
    } catch (IOException e) {
      System.err.println(String.format("ERR: parsing file %s\n%s", sourcePath.toString(), e));
    }
  }

  public static void main(String[] args) {
    ClassScanner classScanner = new ClassScanner();
    for (String arg : args) {
      classScanner.addRoot(Paths.get(arg));
    }
    classScanner.run();
    for (String name : classScanner.getClassNames()) {
      System.out.println(name);
    }
  }

  private static final class ClassVisitor extends VoidVisitorAdapter<Set<String>> {
    private String packageName;

    @Override
    public void visit(PackageDeclaration decl, Set<String> classNames) {
      super.visit(decl, classNames);
      packageName = decl.getName().asString();
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration decl, Set<String> classNames) {
      super.visit(decl, classNames);
      visitType(decl, classNames);
    }

    @Override
    public void visit(EnumDeclaration decl, Set<String> classNames) {
      super.visit(decl, classNames);
      visitType(decl, classNames);
    }

    private void visitType(TypeDeclaration decl, Set<String> classNames) {
      if (!decl.getModifiers().contains(Modifier.PUBLIC)) {
        return;
      }
      String name = decl.getName().getIdentifier();
      Optional<Node> parent = decl.getParentNode();
      while (parent.isPresent() && parent.get() instanceof ClassOrInterfaceDeclaration) {
        name = ((ClassOrInterfaceDeclaration) parent.get()).getName().getIdentifier() + "." + name;
        parent = parent.get().getParentNode();
      }
      if (packageName != null) {
        name = packageName + "." + name;
      }
      classNames.add(name);
    }
  }
}
