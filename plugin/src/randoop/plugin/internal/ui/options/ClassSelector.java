package randoop.plugin.internal.ui.options;

import java.util.ArrayList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.core.runtime.Assert;

import randoop.plugin.RandoopPlugin;

/**
 * The TypeSelector class is used to manage a Tree object so that the user can
 * browse and select or unselect certain IJavaElements. The class is
 * specifically meant for displaying a list of ITypes that can be expanded to
 * show the methods that are contained.
 */
public class ClassSelector {
  private static final String HANDLER_ID = "TypeSelector.HANDLER_ID"; //$NON-NLS-1$
  
  private Tree fTypeTree;

  /**
   * 
   * IMPORTANT: emptyTree must use the SWT.CHECK style bit.
   * 
   * @param typeTree
   *          tree that can be used to display elements
   * @param selectedMethods 
   * @param selectedTypes 
   * @param availableTypes 
   */
  public ClassSelector(Tree typeTree) {
    Assert.isNotNull(typeTree);

    fTypeTree = typeTree;
    fTypeTree.removeAll();

    // Add a selection listener that will check/uncheck descendants of a node
    // when it is checked/unchecked as well as gray some of its ancestors as
    // needed.
    fTypeTree.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        if (event.detail == SWT.CHECK) {
          TreeItem item = (TreeItem) event.item;
          updateTree(item);
        }
      }
    });
  }

  /**
   * 
   * IMPORTANT: emptyTree must use the SWT.CHECK style bit.
   * 
   * @param emptyTree
   *          empty tree that can be used to add
   */
  public ClassSelector(Tree emptyTree, Collection<String> availableTypes, Collection<String> selectedTypes, Collection<String> selectedMethods) {
    this(emptyTree);

    for (String id : availableTypes) {
      IJavaElement element = JavaCore.create(id);
      
      Assert.isTrue(element instanceof IType);
      IType type = (IType) element;

      TreeItem typeItem = addType(type, selectedTypes.contains(id));
      for (TreeItem methodItem : typeItem.getItems()) {
        Object methodId = methodItem.getData(HANDLER_ID);
        if (selectedMethods.contains(methodId)) {
          methodItem.setChecked(true);
          updateTree(methodItem);
        }
      }
    }
  }

  /**
   * Adds the <code>IPackageFragment</code> to the class tree. If the
   * <code>IPackageFragment</code> is already contained in the class tree, the
   * existing <code>TreeItem</code> is returned. Duplicates are not allow.
   * 
   * @param packageFragment
   *          <code>IPackageFragment</code> to add to the class tree
   * @return a new or pre-existing <code>TreeItem</code> containing to <code>IPackageFragment</code>
   */
  public TreeItem addPackage(IPackageFragment packageFragment) {
    // Search for the item in the class tree
    String packageHandlerId = packageFragment.getHandleIdentifier(); 
    for(TreeItem item : fTypeTree.getItems()) {
      String itemHandlerId = (String) item.getData(HANDLER_ID);
      
      if(packageHandlerId.equals(itemHandlerId)) {
        return item;
      }
    }
    
    // Add the package to the class tree since it does not already exist
    TreeItem root = new TreeItem(fTypeTree, SWT.NONE);

    Image image = JavaUI.getSharedImages().getImage(
        ISharedImages.IMG_OBJS_PACKAGE);

    root.setImage(image);

    String packageName = packageFragment.getElementName();
    if (packageName.isEmpty()) {
      packageName = "(default package)";
    }
    root.setText(packageName);
    root.setData(HANDLER_ID, packageFragment.getHandleIdentifier());

    return root;
  }
  
  /**
   * Adds a type to this tree. All of the types methods will also be added as
   * children to this tree.
   * 
   * @param type
   * @return the <code>TreeItem</code> added to the <code>Tree</code> or
   *         <code>null</code> if it was not added
   */
  public TreeItem addType(IType type, boolean checked) {
    try {
      TreeItem parent = addPackage(type.getPackageFragment());
      
      TreeItem root = new TreeItem(parent, SWT.NONE);
      
      Assert.isTrue(!type.isInterface());
      Assert.isTrue(!Flags.isAbstract(type.getFlags()));
      
      Image image = null;
      if (type.isEnum()) {
        image = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_ENUM);
      } else if (type.isClass()) {
        image = JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_CLASS);
      }
      
     root.setImage(image);

      root.setText(type.getFullyQualifiedName());
      root.setData(HANDLER_ID, type.getHandleIdentifier());

      IMethod[] methods = type.getMethods();
      for (IMethod m : methods) {
        TreeItem methodItem = new TreeItem(root, SWT.NONE);
        methodItem.setText(Signature.toString(m.getSignature(),
            m.getElementName(), null, false, true));

        int flags = m.getFlags();
        if (Flags.isPublic(flags)) {
          image = JavaUI.getSharedImages().getImage(
              ISharedImages.IMG_OBJS_PUBLIC);
        } else if (Flags.isPrivate(flags)) {
          image = JavaUI.getSharedImages().getImage(
              ISharedImages.IMG_OBJS_PRIVATE);
        } else if (Flags.isProtected(flags)) {
          image = JavaUI.getSharedImages().getImage(
              ISharedImages.IMG_OBJS_PROTECTED);
        } else {
          image = JavaUI.getSharedImages().getImage(
              ISharedImages.IMG_OBJS_DEFAULT);
        }

        methodItem.setImage(image);
        methodItem.setData(HANDLER_ID, m.getHandleIdentifier());
      }

      setChecked(root, checked);
      return root;
      
    } catch (JavaModelException e) {
      RandoopPlugin.log(e);
      return null;
    }
  }

  /**
   * 
   * @return list of handlers for types that are fully checked (not grayed)
   */
  public List<String> getCheckedClasses() {
    List<String> classes = new ArrayList<String>();

    for (TreeItem packageItem : fTypeTree.getItems()) {
      for (TreeItem classItem : packageItem.getItems()) {
        if (classItem.getChecked() && !classItem.getGrayed()) {
          classes.add((String) classItem.getData(HANDLER_ID));
        }
      }
    }

    return classes;
  }

  /**
   * Returns a list of <code>IMethod</code>s that are checked, but whose
   * containing types are grayed.
   * 
   * @return list
   */
  public List<String> getCheckedMethods() {
    List<String> types = new ArrayList<String>();

    for (TreeItem packageItem : fTypeTree.getItems()) {
      for (TreeItem classItem : packageItem.getItems()) {
        for (TreeItem methodItem : classItem.getItems()) {
          if (methodItem.getChecked() && !methodItem.getGrayed()) {
            // According to typical tree behavior, the parent item must be checked
            Assert.isTrue(classItem.getChecked());
            
            // Only add the method if its containing class is grayed
            if (methodItem.getParentItem().getGrayed()) {
              types.add((String) methodItem.getData(HANDLER_ID));
            }
          }
        }
      }
    }

    return types;
  }
  
  public List<String> getAllTypes() {
    List<String> types = new ArrayList<String>();

    for (TreeItem packageItem : fTypeTree.getItems()) {
      for (TreeItem classItem : packageItem.getItems()) {
        types.add((String) classItem.getData(HANDLER_ID));
      }
    }
    
    return types;
  }

  /**
   * Updates the checked state of this item and its descendants and ancestors.
   * 
   * @param item
   *          the TreeItem which has just been changed
   * @param checked
   *          the new checked state of item
   */
  protected void setChecked(TreeItem item, boolean checked) {
    item.setChecked(checked);
    updateTree(item);
  }

  /**
   * Updates the checked state of this item's descendants and ancestors.
   * 
   * @param item
   *          the TreeItem for whom the descendants and ancestors will update
   */
  protected void updateTree(TreeItem item) {
    // Items that have just been checked should never be grayed.
    item.setGrayed(false);

    if (item != null) {
      boolean checked = item.getChecked();
      checkItems(item, checked);
      checkPath(item.getParentItem(), item.getChecked(), false);
    }
  }

  /**
   * Recursively iterates backwards through the tree to update the grayed status
   * of each. This code has been adapted from SWT snippet 274.
   * 
   * @param parent
   *          the item to examine, all ancestors will also be examined
   * @param childChecked
   *          true if this item is checked
   * @param childGrayed
   *          true if this item is grayed
   */
  private static void checkPath(TreeItem parent, boolean childChecked,
      boolean childGrayed) {
    boolean parentChecked = childChecked;
    boolean parentGrayed = childGrayed;

    if (parent == null)
      return;
    if (childGrayed) {
      // If the child item is grayed, then it must also have been checked
      // (grayed items are not visible unless they are also checked). In this
      // case, we already know that this parent item must also be grayed since
      // at least one of its children is already grayed.
      parentChecked = true;
    } else {
      // Otherwise, if the child item was not gray, it could have either be
      // fully checked or fully unchecked, meaning that this parent item could
      // also be in the same state as the child that called this routine. First,
      // we must examine each other child element to see if this item should be
      // grayed.
      TreeItem[] items = parent.getItems();
      for (int i = 0; i < items.length; i++) {
        TreeItem child = items[i];
        if (child.getGrayed() || childChecked != child.getChecked()) {
          parentChecked = true;
          parentGrayed = true;
          break;
        }
      }
    }
    
    parent.setChecked(parentChecked);
    parent.setGrayed(parentGrayed);
    checkPath(parent.getParentItem(), childChecked, childGrayed);
  }

  /**
   * Checks/unchecks this item and all of its descendants. This code has been
   * adapted from SWT snippet 274.
   * 
   * @param item
   * @param checked
   */
  private static void checkItems(TreeItem item, boolean checked) {
    if (item == null)
      return;
    item.setGrayed(false);
    item.setChecked(checked);

    // check/uncheck all of this TreeItem's children as well
    TreeItem[] items = item.getItems();
    for (int i = 0; i < items.length; i++) {
      checkItems(items[i], checked);
    }
  }

  TreeItem[] getMoveableSelection() {
    TreeItem[] roots = getSelectedPackageFragments();
    TreeItem[] classes = getSelectedClasses();
    
    if (roots.length != 0 && classes.length == 0) {
      return roots;
    } else if (roots.length == 0 && classes.length != 0) {
      return classes;
    } else {
      return null;
    }
  }
  
  private TreeItem[] getSelectedPackageFragments() {
    TreeItem[] items = fTypeTree.getSelection();
    List<TreeItem> roots = new ArrayList<TreeItem>();

    for (TreeItem item : items) {
      // Only move root elements
      if (item.getParentItem() == null) {
        roots.add(item);
      }
    }

    return roots.toArray(new TreeItem[roots.size()]);
  }
  
  private TreeItem[] getSelectedClasses() {
    TreeItem[] items = fTypeTree.getSelection();
    List<TreeItem> classes = new ArrayList<TreeItem>();

    for (TreeItem item : items) {
      if (item.getParentItem() != null && item.getParentItem().getParentItem() == null) {
        classes.add(item);
      }
    }

    return classes.toArray(new TreeItem[classes.size()]);
  }
  
  public void moveSelectedTypesUp() {
    moveSelectedTypes(true);
  }

  public void moveSelectedTypesDown() {
    moveSelectedTypes(false);
  }
  
  private void moveSelectedTypes(boolean up) {
    TreeItem[] selectedRoots = getMoveableSelection();

    if (selectedRoots != null) {
      if (up) {
        for (int i = 0; i < selectedRoots.length; i++) {
          moveItem(selectedRoots[i], true);
        }
      } else {
        for (int i = selectedRoots.length - 1; i >= 0; i--) {
          moveItem(selectedRoots[i], false);
        }
      }
    }
  }
  
  private TreeItem moveItem(TreeItem oldItem, boolean up) {
    TreeItem parent = oldItem.getParentItem();
    
    int direction = up ? -1 : 2;
    TreeItem newItem;
    if (parent == null) {
      int index = fTypeTree.indexOf(oldItem);
      newItem = new TreeItem(fTypeTree, SWT.NONE, index + direction);
    } else {
      int index = parent.indexOf(oldItem);
      newItem = new TreeItem(parent, SWT.NONE, index + direction);
    }
    
    copy(oldItem, newItem);
    
    return newItem;
  }

  private void copy(TreeItem oldItem, TreeItem newItem) {
    newItem.setChecked(oldItem.getChecked());
    newItem.setGrayed(oldItem.getGrayed());
    newItem.setText(oldItem.getText());
    newItem.setImage(oldItem.getImage());
    newItem.setData(HANDLER_ID, oldItem.getData(HANDLER_ID));

    TreeItem children[] = oldItem.getItems();
    for (TreeItem oldChild : children) {
      TreeItem newChild = new TreeItem(newItem, SWT.NONE);
      copy(oldChild, newChild);
      
      oldChild.dispose();
    }
    
    oldItem.dispose();
  }

  /**
   * Returns <code>true</code> if some of the selected <code>TreeItem</code>s
   * can be removed from the <code>Tree</code>
   */
  boolean canRemoveFromSelection() {
    TreeItem[] items = fTypeTree.getSelection();

    for (TreeItem item : items) {
      // Only remove package fragments or classes (roots or their children)
      if (item.getParentItem() == null || item.getParentItem().getParentItem() == null) {
        return true;
      }
    }
    
    return false;
  }
  
  public void removeSelectedTypes() {
    TreeItem[] items = fTypeTree.getSelection();

    for (TreeItem item : items) {
      // Only remove package fragments or classes (roots or their children)
      if (item.getParentItem() == null) {
        item.dispose();
      } else if (item.getParentItem().getParentItem() == null) {
        TreeItem parent = item.getParentItem();
        item.dispose();
        
        // If their are not classes left in the package fragment, dispose it
        if(parent.getItemCount() == 0) {
          parent.dispose();
        }
      }
    }
  }
}