package net.mostlyoriginal.game.system.logic.analysis;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.utils.ImmutableBag;
import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.PathFinderRequest;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.utils.TimeUtils;
import net.mostlyoriginal.api.component.basic.Bounds;
import net.mostlyoriginal.api.component.basic.Pos;
import net.mostlyoriginal.api.utils.reference.SafeEntityReference;
import net.mostlyoriginal.game.Path;
import net.mostlyoriginal.game.api.DelayedEntitySystem;
import net.mostlyoriginal.game.api.pathfinding.GridGraph;
import net.mostlyoriginal.game.api.pathfinding.GridNode;
import net.mostlyoriginal.game.api.pathfinding.GridNodeEuclideanHeuristic;
import net.mostlyoriginal.game.component.Routable;
import net.mostlyoriginal.game.component.Team;
import net.mostlyoriginal.game.manager.LayerManager;
import net.mostlyoriginal.game.manager.NavigationGridManager;
import net.mostlyoriginal.game.system.LayerLoaderSystem;
import org.xguzm.pathfinding.grid.GridCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Resolve routes between all nodes.
 *
 * @author Daan van Yperen
 */
@Wire(injectInherited = true)
public class RouteCalculationSystem extends DelayedEntitySystem {

	protected ComponentMapper<Pos> mPos;
	protected ComponentMapper<Routable> mRoutable;
	protected ComponentMapper<Bounds> mBounds;
	private NavigationGridManager navigationGridManager;
	private NavigationGridCalculationSystem navigationGridCalculationSystem;
	private LayerLoaderSystem layerLoaderSystem;


	@SuppressWarnings("unchecked")
	public RouteCalculationSystem() {
		super(Aspect.getAspectForAll(Routable.class, Pos.class));
	}

	@Override
	protected boolean prerequisitesMet() {
		return super.prerequisitesMet() && layerLoaderSystem.processed;
	}

	@Override
	protected void initialize() {

		setPrerequisiteSystems(navigationGridCalculationSystem);

		//create a finder either using the default options
	}

	@Override
	protected void collectJobs(ImmutableBag<Entity> entities, LinkedList<Job> jobs) {

		// clear existing routes.
		for (int i = 0, s = entities.size(); i < s; i++) {
			for (Team team : Team.values()) {
				mRoutable.get(entities.get(i)).paths.get(team).clear();
			}
		}

		for (Team team : Team.values()) {
			int size = entities.size();
			GridGraph graph = navigationGridManager.getNavigationGrid(team);
			IndexedAStarPathFinder<GridNode> finder = new IndexedAStarPathFinder<GridNode>(graph);
			for (int a = 0; a < size; a++) {
				for (int b = a + 1; b < size; b++) {
					jobs.add(new resolveRouteJob(finder, graph, entities.get(a), entities.get(b), team));
				}
			}
		}

		jobs.add(new SortRoutesJob());
	}


	/**
	 * Job to resolve route between two entities.
	 */
	private class resolveRouteJob implements Job {

		private IndexedAStarPathFinder<GridNode> finder;
		private GridGraph graph;
		private final Entity a;
		private final Entity b;
		private final Team team;
		private PathFinderRequest<GridNode> request;
		private boolean finished;

		public resolveRouteJob(IndexedAStarPathFinder<GridNode> finder, GridGraph graph, Entity a, Entity b, Team team) {
			this.finder = finder;
			this.graph = graph;
			this.a = a;
			this.b = b;
			this.team = team;
		}

		@Override
		public void run() {

			if (!finished) {

				if (request == null) {

					if (!a.isActive() || !b.isActive()) return;

					// offset to center on the image, and convert to pathing space.
					// @todo cleanup the space difference.
					final Pos posA = mPos.get(a);
					final Pos posB = mPos.get(b);

					Bounds boundsA = mBounds.get(a);
					Bounds boundsB = mBounds.get(b);

					int aX = (int) (posA.x + boundsA.cx()) / LayerManager.CELL_SIZE;
					int aY = (int) (posA.y + boundsA.cy()) / LayerManager.CELL_SIZE;
					final GridNode cellA = graph.get(aX, aY);

					int bX = (int) (posB.x + boundsB.cx()) / LayerManager.CELL_SIZE;
					int bY = (int) (posB.y + boundsB.cy()) / LayerManager.CELL_SIZE;
					final GridNode cellB = graph.get(bX, bY);

					mRoutable.get(a).setX(aX);
					mRoutable.get(a).setY(aY);
					mRoutable.get(b).setX(bX);
					mRoutable.get(b).setY(bY);

					request = new PathFinderRequest<GridNode>(cellA, cellB, new GridNodeEuclideanHeuristic(), new DefaultGraphPath<GridNode>(800));
					request.changeStatus(PathFinderRequest.SEARCH_INITIALIZED);
				}

				// Allow spending 25 ms (approx 40 times per second)
				if ( finder.search(request, TimeUtils.millisToNanos(25))) {
					finished = true;
					if (request.pathFound) {

						// @TODO replace legacy usage of GridCell.
						final LinkedList<GridCell> cells = new LinkedList<>();
						for (Object node : request.resultPath) {
							cells.add(new GridCell(((GridNode) node).x, ((GridNode) node).y));
						}

						final Path toDestination = new Path(new SafeEntityReference(b), cells, team, false);
						ArrayList<GridCell> reversedCells = new ArrayList<GridCell>(cells);
						Collections.reverse(reversedCells);
						final Path toSource = new Path(new SafeEntityReference(a), reversedCells, team, true);
						mRoutable.get(a).paths.get(team).add(toDestination);
						mRoutable.get(b).paths.get(team).add(toSource);

						request = null;
					}
				}

				if ( request != null ) request.statusChanged = false;
			}
		}

		@Override
		public boolean isCompleted() {
			return finished;
		}

	}


	@Override
	protected long maxDuration() {
		// routes to calculate each run.
		return 0;
	}

	private class SortRoutesJob implements Job {
		@Override
		public void run() {
			ImmutableBag<Entity> actives = getActives();
			for (int i = 0, s = actives.size(); i < s; i++) {
				// sort paths by shortest to longest.
				final Routable routable = mRoutable.get(actives.get(i));
				for (Team team : Team.values()) {
					Collections.sort(routable.paths.get(team));
				}
			}
		}

		@Override
		public boolean isCompleted() {
			return true;
		}
	}

}
