package net.mostlyoriginal.game.api;

import com.badlogic.gdx.utils.Array;
import net.mostlyoriginal.game.system.logic.analysis.NavigationGridGdx;
import org.xguzm.pathfinding.*;
import org.xguzm.pathfinding.grid.GridCell;

import java.util.Comparator;
import java.util.List;

/**
 * A generic implementation of A* that works on any {@link org.xguzm.pathfinding.NavigationGraph} instance.
 *
 * @author Xavier Guzman
 *
 * @param <T> a class implementing {@link org.xguzm.pathfinding.NavigationNode}
 */
public class AStarFinderGdx<T extends GridCell> implements PathFinder<T> {

	private PathFinderOptions defaultOptions;
	BHeap<T> openList;
	public int jobId;

	public AStarFinderGdx(Class<T> clazz, PathFinderOptions opt) {
		this.defaultOptions = opt ;
		openList = new BHeap<T>(new Comparator<T>() {
			@Override
			public int compare(T o1, T o2) {
				if (o1 == null || o2 == null){
					if (o1 == o2)
						return 0;
					if (o1 == null)
						return -1;
					else
						return 1;

				}
				return (int)(o1.getF() - o2.getF());
			}
		});
	}

	Array<T> neighbors = new Array<T>();

	public List<T> findPath(T startNode, T endNode, NavigationGraph<T> graph) {

		if (jobId == Integer.MAX_VALUE)
			jobId = 0;
		int job = ++jobId;

		T node, neighbor;

		float ng;

		startNode.setG(0);
		startNode.setF(0);

		// push the start node into the open list
		openList.clear();
		openList.add(startNode);
		startNode.setParent(null);
		startNode.setOpenedOnJob( job );

		while (openList.size > 0) {

			// pop the position of node which has the minimum 'f' value.
			node = openList.pop();
			node.setClosedOnJob(job);


			// if reached the end position, construct the path and return it
			if (node == endNode) {
				return Util.backtrace(endNode);
			}

			// get neighbors of the current node
			neighbors.clear();
			neighbors.addAll(((NavigationGridGdx) graph).getNeighborsAsArray((GridCell) node, defaultOptions));
			for (int i = 0, l = neighbors.size; i < l; ++i) {
				neighbor = neighbors.get(i);

				if (neighbor.getClosedOnJob() == job || !graph.isWalkable(neighbor)) {
					continue;
				}

				// get the distance between current node and the neighbor and calculate the next g score
				ng = node.getG() + (node.x == neighbor.x || node.y == neighbor.y  ?  1f : 1.4f);

				// check if the neighbor has not been inspected yet, or can be reached with smaller cost from the current node
				if (neighbor.getOpenedOnJob() != job || ng < neighbor.getG()) {
					float prevf = neighbor.getF();
					neighbor.setG(ng);

					neighbor.setH(defaultOptions.heuristic.calculate(neighbor, endNode));
					neighbor.setF( neighbor.getG() + neighbor.getH());
					neighbor.setParent(node);

					if (neighbor.getOpenedOnJob() != job) {
						openList.add(neighbor);
						neighbor.setOpenedOnJob(job);
					} else {
						// the neighbor can be reached with smaller cost.
						// Since its f value has been updated, we have to update its position in the open list
						openList.updateNode(neighbor, neighbor.getF() - prevf);
					}
				}
			}
		}

		// fail to find the path
		return null;
	}
}
