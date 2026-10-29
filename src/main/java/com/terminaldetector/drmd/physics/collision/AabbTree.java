package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;

/** Mutable full-volume broadphase. Each object has one leaf, regardless of its size or world Y.
 * Queries are exact over the supplied bounds: collision candidates are never sampled or truncated.
 * Owners update it on their world thread; local collider BVHs remain separate and unchanged. */
public final class AabbTree<T> {
    public record Query<T>(List<T> values,int visitedNodes) {
        public Query {values=List.copyOf(values);}
    }
    private static final double MARGIN=.25;
    private static final class Node<T> {
        BodyBounds bounds,exact;
        T value;
        Node<T> parent,left,right;
        int height;
        boolean leaf(){return left==null;}
    }
    private final Map<T,Node<T>> leaves=new HashMap<>();
    private Node<T> root;
    public int size(){return leaves.size();}
    public int height(){return root==null?-1:root.height;}
    public void clear(){leaves.clear();root=null;}

    /** Returns true only when the tree topology needed an insertion or relocation. */
    public boolean update(T value,BodyBounds bounds) {
        Objects.requireNonNull(value);Objects.requireNonNull(bounds);
        Node<T> leaf=leaves.get(value);
        if(leaf!=null&&contains(leaf.bounds,bounds)){leaf.exact=bounds;return false;}
        var fat=expand(bounds,MARGIN); // Validate before mutating the existing tree.
        if(leaf==null){leaf=new Node<>();leaf.value=value;leaves.put(value,leaf);}
        else detach(leaf);
        leaf.exact=bounds;leaf.bounds=fat;leaf.height=0;insert(leaf);return true;
    }
    public boolean remove(T value) {
        Node<T> leaf=leaves.remove(value);if(leaf==null)return false;detach(leaf);return true;
    }
    public Query<T> query(BodyBounds bounds) {
        Objects.requireNonNull(bounds);var found=new ArrayList<T>();int visited=0;
        if(root==null)return new Query<>(found,0);
        var stack=new ArrayDeque<Node<T>>();stack.push(root);
        while(!stack.isEmpty()) {
            var n=stack.pop();visited++;
            if(!n.bounds.intersects(bounds))continue;
            if(n.leaf()){if(n.exact.intersects(bounds))found.add(n.value);}
            else {stack.push(n.right);stack.push(n.left);}
        }
        return new Query<>(found,visited);
    }
    private void insert(Node<T> leaf) {
        if(root==null){root=leaf;leaf.parent=null;return;}
        Node<T> sibling=root;
        while(!sibling.leaf()) {
            double old=area(sibling.bounds),combined=area(sibling.bounds.union(leaf.bounds));
            double inherited=2*(combined-old);
            double left=cost(sibling.left,leaf.bounds,inherited),right=cost(sibling.right,leaf.bounds,inherited);
            if(2*combined<left&&2*combined<right)break;
            sibling=left<=right?sibling.left:sibling.right;
        }
        var parent=new Node<T>();parent.parent=sibling.parent;parent.left=sibling;parent.right=leaf;
        parent.bounds=sibling.bounds.union(leaf.bounds);parent.height=sibling.height+1;
        replace(sibling,parent);sibling.parent=parent;leaf.parent=parent;refit(parent);
    }
    private static double cost(Node<?> child,BodyBounds bounds,double inherited) {
        double combined=area(child.bounds.union(bounds));
        return inherited+(child.leaf()?combined:combined-area(child.bounds));
    }
    private void detach(Node<T> leaf) {
        if(leaf==root){root=null;leaf.parent=null;return;}
        Node<T> parent=leaf.parent,sibling=parent.left==leaf?parent.right:parent.left,grand=parent.parent;
        replace(parent,sibling);sibling.parent=grand;leaf.parent=null;
        if(grand!=null)refit(grand);
    }
    /** Replace a node in its current parent (or at the root), before changing parent links. */
    private void replace(Node<T> before,Node<T> after) {
        if(before.parent==null)root=after;
        else if(before.parent.left==before)before.parent.left=after;
        else before.parent.right=after;
    }
    private void refit(Node<T> node) {
        while(node!=null) {
            node=balance(node);
            if(!node.leaf())updateBounds(node);
            node=node.parent;
        }
    }
    private static void updateBounds(Node<?> n) {
        n.bounds=n.left.bounds.union(n.right.bounds);n.height=1+Math.max(n.left.height,n.right.height);
    }
    /** AVL-style rotations keep sequential insertions from degenerating into a linked list. */
    private Node<T> balance(Node<T> a) {
        if(a.leaf()||a.height<2)return a;
        Node<T> b=a.left,c=a.right;
        if(c.height-b.height>1) {
            Node<T> f=c.left,g=c.right,oldParent=a.parent;
            replace(a,c);c.parent=oldParent;c.left=a;a.parent=c;
            if(f.height>g.height){c.right=f;f.parent=c;a.right=g;g.parent=a;}
            else {c.right=g;g.parent=c;a.right=f;f.parent=a;}
            updateBounds(a);updateBounds(c);return c;
        }
        if(b.height-c.height>1) {
            Node<T> d=b.left,e=b.right,oldParent=a.parent;
            replace(a,b);b.parent=oldParent;b.right=a;a.parent=b;
            if(d.height>e.height){b.left=d;d.parent=b;a.left=e;e.parent=a;}
            else {b.left=e;e.parent=b;a.left=d;d.parent=a;}
            updateBounds(a);updateBounds(b);return b;
        }
        return a;
    }
    private static double area(BodyBounds b) {
        Vec3 span=b.max().minus(b.min());return 2*(span.x()*span.y()+span.x()*span.z()+span.y()*span.z());
    }
    private static boolean contains(BodyBounds outer,BodyBounds inner) {
        return outer.min().x()<=inner.min().x()&&outer.min().y()<=inner.min().y()&&outer.min().z()<=inner.min().z()
            &&outer.max().x()>=inner.max().x()&&outer.max().y()>=inner.max().y()&&outer.max().z()>=inner.max().z();
    }
    private static BodyBounds expand(BodyBounds b,double margin) {
        var d=new Vec3(margin,margin,margin);return new BodyBounds(b.min().minus(d),b.max().plus(d));
    }
}
