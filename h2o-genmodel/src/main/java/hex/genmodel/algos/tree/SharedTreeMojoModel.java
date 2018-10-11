package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;

/**
 * Common ancestor for {@link DrfMojoModel} and {@link GbmMojoModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class SharedTreeMojoModel extends MojoModel implements SharedTreeGraphConverter{
    protected double _mojo_version;
    private ScoreTree _scoreTree;

    /**
     * {@code _ntree_groups} is the number of trees requested by the user. For
     * binomial case or regression this is also the total number of trees
     * trained; however in multinomial case each requested "tree" is actually
     * represented as a group of trees, with {@code _ntrees_per_group} trees
     * in each group. Each of these individual trees assesses the likelihood
     * that a given observation belongs to class A, B, C, etc. of a
     * multiclass response.
     */
    protected int _ntree_groups;
    protected int _ntrees_per_group;
    /**
     * Array of binary tree data, each tree being a {@code byte[]} array. The
     * trees are logically grouped into a rectangular grid of dimensions
     * {@link #_ntree_groups} x {@link #_ntrees_per_group}, however physically
     * they are stored as 1-dimensional list, and an {@code [i, j]} logical
     * tree is mapped to the index {@link #treeIndex(int, int)}.
     */
    protected byte[][] _compressed_trees;

    /**
     * Array of auxiliary binary tree data, each being a {@code byte[]} array.
     */
    protected byte[][] _compressed_trees_aux;

    /**
     * GLM's beta used for calibrating output probabilities using Platt Scaling.
     */
    protected double[] _calib_glm_beta;


    protected void postInit() {
      if (_mojo_version == 1.0) {
        _scoreTree = new ScoreTree0(); // First version
      } else if (_mojo_version == 1.1) {
        _scoreTree = new ScoreTree1(); // Second version
      } else
        _scoreTree = new ScoreTree2(); // Current version
    }

    public final int getNTreeGroups() {
      return _ntree_groups;
    }

    public final int getNTreesPerGroup() {
      return _ntrees_per_group;
    }

    private static final ScoreTree SCORE_TREE_IMPL_V0 = new ScoreTree0();
    /**
     * @deprecated use {@link ScoreTree0#scoreTree(byte[], double[], boolean, String[][])} instead.
     */
    @Deprecated
    public static double scoreTree0(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
      // note that nclasses is ignored (and in fact, always was)
      return SCORE_TREE_IMPL_V0.scoreTree(tree, row, computeLeafAssignment, new String[0][]);
    }

    private static final ScoreTree SCORE_TREE_IMPL_V1 = new ScoreTree1();
    /**
     * @deprecated use {@link ScoreTree1#scoreTree(byte[], double[], boolean, String[][])} instead.
     */
    @Deprecated
    public static double scoreTree1(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
      // note that nclasses is ignored (and in fact, always was)
      return SCORE_TREE_IMPL_V1.scoreTree(tree, row, computeLeafAssignment, new String[0][]);
    }

    /**
     * @deprecated use {@link #scoreTree(byte[], double[], boolean, String[][])} instead.
     */
    @Deprecated
    public static double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment, String[][] domains) {
      // note that {@link nclasses} is ignored (and in fact, always was)
      return scoreTree(tree, row, computeLeafAssignment, domains);
    }

    private static final ScoreTree SCORE_TREE_IMPL_PREFERRED = new ScoreTree2();

    public static double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains) {
        return SCORE_TREE_IMPL_PREFERRED.scoreTree(tree, row, computeLeafAssignment, domains);
    }

    public interface DecisionPathTracker<T> {
        boolean go(int depth, boolean right);
        T terminate();
    }

    public static class StringDecisionPathTracker implements DecisionPathTracker<String> {
        private final char[] _sb = new char[64];
        private int _pos = 0;
        @Override
        public boolean go(int depth, boolean right) {
            _sb[depth] = right ? 'R' : 'L';
            if (right) _pos = depth;
            return true;
        }
        @Override
        public String terminate() {
            String path = new String(_sb, 0, _pos);
            _pos = 0;
            return path;
        }
    }

    public static <T> T getDecisionPath(double leafAssignment, DecisionPathTracker<T> tr) {
      return ScoreTree2.getDecisionPath(leafAssignment, tr);
    }

    public static String getDecisionPath(double leafAssignment) {
      return ScoreTree2.getDecisionPath(leafAssignment);
    }

    public static int getLeafNodeId(double leafAssignment, byte[] auxTree) {
      return ScoreTree2.getLeafNodeId(leafAssignment, auxTree);
    }

    /**
     * Compute a graph of the forest.
     *
     * @return A graph of the forest.
     */
    public SharedTreeGraph _computeGraph(int treeToPrint) {
        SharedTreeGraph g = new SharedTreeGraph();

        if (treeToPrint >= _ntree_groups) {
            throw new IllegalArgumentException("Tree " + treeToPrint + " does not exist (max " + _ntree_groups + ")");
        }

        int j;
        if (treeToPrint >= 0) {
            j = treeToPrint;
        }
        else {
            j = 0;
        }

        for (; j < _ntree_groups; j++) {
            for (int i = 0; i < _ntrees_per_group; i++) {
                int itree = treeIndex(j, i);
                String[] domainValues = isSupervised() ? getDomainValues(getResponseIdx()) : null;
                String treeName = treeName(j, i, domainValues);
                SharedTreeSubgraph sg = g.makeSubgraph(treeName);
                ScoreTree2.computeTreeGraph(sg, _compressed_trees[itree], _compressed_trees_aux[itree],
                        getNames(), getDomainValues());
            }

            if (treeToPrint >= 0) {
                break;
            }
        }

        return g;
    }

    public static SharedTreeSubgraph computeTreeGraph(int treeNum, String treeName, byte[] tree, byte[] auxTreeInfo,
                                                      String names[], String[][] domains) {
      SharedTreeSubgraph sg = new SharedTreeSubgraph(treeNum, treeName);
      ScoreTree2.computeTreeGraph(sg, tree, auxTreeInfo, names, domains);
      return sg;
    }

    public static String treeName(int groupIndex, int classIndex, String[] domainValues) {
      String className = "";
      {
        if (domainValues != null) {
          className = ", Class " + domainValues[classIndex];
        }
      }
      return "Tree " + groupIndex + className;
    }

    //------------------------------------------------------------------------------------------------------------------
    // Private
    //------------------------------------------------------------------------------------------------------------------

    protected SharedTreeMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    /**
     * Score all trees and fill in the `preds` array.
     */
    protected void scoreAllTrees(double[] row, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        scoreTreeRange(row, 0, _ntree_groups, preds);
    }

    /**
     * Transforms tree predictions into the final model predictions.
     * For classification: converts tree preds into probability distribution and picks predicted class.
     * For regression: projects tree prediction from link-space into the original space.
     * @param row input row.
     * @param offset offset.
     * @param preds final output, same structure as of {@link SharedTreeMojoModel#score0}.
     * @return preds array.
     */
    public abstract double[] unifyPreds(double[] row, double offset, double[] preds);

  /**
   * Generates a (per-class) prediction using only a single tree.
   * @param row input row
   * @param index index of the tree (0..N-1)
   * @param preds array of partial predictions.
   */
    public final void scoreSingleTree(double[] row, int index, double preds[]) {
      scoreTreeRange(row, index, index + 1, preds);
    }

    /**
     * Generates (partial, per-class) predictions using only trees from a given range.
     * @param row input row
     * @param fromIndex low endpoint (inclusive) of the tree range
     * @param toIndex high endpoint (exclusive) of the tree range
     * @param preds array of partial predictions.
     *              To get final predictions pass the result to {@link SharedTreeMojoModel#unifyPreds}.
     */
    public final void scoreTreeRange(double[] row, int fromIndex, int toIndex, double[] preds) {
        final int clOffset = _nclasses == 1 ? 0 : 1;
        for (int classIndex = 0; classIndex < _ntrees_per_group; classIndex++) {
            int k = clOffset + classIndex;
            int itree = treeIndex(fromIndex, classIndex);
            for (int groupIndex = fromIndex; groupIndex < toIndex; groupIndex++) {
                if (_compressed_trees[itree] != null) { // Skip all empty trees
                  preds[k] += _scoreTree.scoreTree(_compressed_trees[itree], row, false, _domains);
                }
                itree++;
            }
        }
    }

    // note that _ntree_group = _treekeys.length
    // ntrees_per_group = _treeKeys[0].length
    public String[] getDecisionPathNames() {
      int classTrees = 0;
      for (int i = 0; i < _ntrees_per_group; ++i) {
        int itree = treeIndex(0, i);
        if (_compressed_trees[itree] != null) classTrees++;
      }
      final int outputcols = _ntree_groups * classTrees;
      final String[] names = new String[outputcols];
      for (int c = 0; c < _ntrees_per_group; c++) {
        for (int tidx = 0; tidx < _ntree_groups; tidx++) {
          int itree = treeIndex(tidx, c);
          if (_compressed_trees[itree] != null) {
            names[itree] = "T" + (tidx + 1) + ".C" + (c + 1);
          }
        }
      }
      return names;
    }

    public static class LeafNodeAssignments {
      public String[] _paths;
      public int[] _nodeIds;
    }

    public LeafNodeAssignments getLeafNodeAssignments(final double[] row) {
      LeafNodeAssignments assignments = new LeafNodeAssignments();
      assignments._paths = new String[_compressed_trees.length];
      if (_mojo_version >= 1.3 && _compressed_trees_aux != null) { // enable only for compatible MOJOs
        assignments._nodeIds = new int[_compressed_trees_aux.length];
      }
      traceDecisions(row, assignments._paths, assignments._nodeIds);
      return assignments;
    }

    public String[] getDecisionPath(final double[] row) {
      String[] paths = new String[_compressed_trees.length];
      traceDecisions(row, paths, null);
      return paths;
    }

    private void traceDecisions(final double[] row, String[] paths, int[] nodeIds) {
      if (_mojo_version < 1.2) {
        throw new IllegalArgumentException("You can only obtain decision tree path with mojo versions 1.2 or higher");
      }
      for (int j = 0; j < _ntree_groups; j++) {
        for (int i = 0; i < _ntrees_per_group; i++) {
          int itree = treeIndex(j, i);
          double d = scoreTree(_compressed_trees[itree], row, true, _domains);
          if (paths != null)
            paths[itree] = SharedTreeMojoModel.getDecisionPath(d);
          if (nodeIds != null) {
            assert _mojo_version >= 1.3;
            nodeIds[itree] = SharedTreeMojoModel.getLeafNodeId(d, _compressed_trees_aux[itree]);
          }
        }
      }
    }

    /**
     * Locates a tree in the array of compressed trees.
     * @param groupIndex index of the tree in a class-group of trees
     * @param classIndex index of the class
     * @return index of the tree in _compressed_trees.
     */
    final int treeIndex(int groupIndex, int classIndex) {
        return classIndex * _ntree_groups + groupIndex;
    }


  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  /////////////////////////////////////////////////////

  @Override
  public boolean calibrateClassProbabilities(double[] preds) {
    if (_calib_glm_beta == null)
      return false;
    assert _nclasses == 2; // only supported for binomial classification
    assert preds.length == _nclasses + 1;
    double p = GLM_logitInv((preds[1] * _calib_glm_beta[0]) + _calib_glm_beta[1]);
    preds[1] = 1 - p;
    preds[2] = p;
    return true;
  }

    @Override
    public SharedTreeGraph convert(final int treeNumber, final String treeClass) {
        return _computeGraph(treeNumber);
    }

}
