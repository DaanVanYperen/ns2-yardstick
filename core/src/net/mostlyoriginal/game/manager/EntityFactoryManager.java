package net.mostlyoriginal.game.manager;

import com.artemis.*;
import com.artemis.annotations.Wire;
import com.artemis.managers.TagManager;
import com.artemis.utils.EntityBuilder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapProperties;
import net.mostlyoriginal.api.component.basic.Bounds;
import net.mostlyoriginal.api.component.basic.Pos;
import net.mostlyoriginal.api.component.graphics.Anim;
import net.mostlyoriginal.api.component.graphics.Renderable;
import net.mostlyoriginal.api.event.common.EventManager;
import net.mostlyoriginal.game.component.*;
import net.mostlyoriginal.game.component.buildings.ResourceNode;
import net.mostlyoriginal.game.component.buildings.Techpoint;
import net.mostlyoriginal.game.component.ui.*;
import net.mostlyoriginal.game.events.DragEvent;
import net.mostlyoriginal.game.events.DrawEvent;
import net.mostlyoriginal.game.system.logic.ToolSystem;
import net.mostlyoriginal.game.system.logic.analysis.NavigationGridCalculationSystem;

import java.util.EnumSet;

/**
 * Game specific entity factory.
 *
 * @todo transform this into a manager.
 * @author Daan van Yperen
 */
@Wire
public class EntityFactoryManager extends Manager {

    private Archetype resourceNode;
    private Archetype techpoint;
    private Archetype duct;

    private Archetype marine;
    private Archetype alien;

    protected ComponentMapper<Pos> mPos;
    protected ComponentMapper<Anim> mAnim;
    protected ComponentMapper<Bounds> mBounds;
    protected ComponentMapper<Blockade> mBlockade;
    protected ComponentMapper<Draggable> mDraggable;
    protected ComponentMapper<Renderable> mRenderable;
    protected ComponentMapper<TeamMember> mTeamMember;
    protected ComponentMapper<Persistable> mPersistable;
    protected ComponentMapper<RenderMask> mRenderMask;
    private ComponentMapper<Routable> mRoutable;
    private TagManager tagManager;


    @Override
    @SuppressWarnings("unchecked")
    protected void initialize() {
        super.initialize();

        resourceNode = new ArchetypeBuilder().add(
                Pos.class,
                Anim.class,
                Bounds.class,
                Renderable.class,
                TeamAsset.class,
                Deletable.class,
                Draggable.class,
                Clickable.class,
                Persistable.class,
                RenderMask.class,
                ResourceNode.class,
                TeamMember.class,
                Routable.class
        ).build(world);
        techpoint = new ArchetypeBuilder().add(
                Pos.class,
                Anim.class,
                Bounds.class,
                Draggable.class,
                Renderable.class,
                TeamAsset.class,
                Deletable.class,
                Clickable.class,
                Persistable.class,
                RenderMask.class,
                Routable.class,
                TeamMember.class,
                Techpoint.class
        ).build(world);
        marine = new ArchetypeBuilder().add(
                Pos.class,
                Anim.class,
                Bounds.class,
                Traveler.class,
                TeamMember.class,
                Transient.class,
                RenderMask.class,
                Renderable.class
        ).build(world);
        alien = new ArchetypeBuilder().add(
                Pos.class,
                Anim.class,
                Bounds.class,
                Traveler.class,
                TeamMember.class,
                Transient.class,
                RenderMask.class,
                Renderable.class
        ).build(world);
        duct = new ArchetypeBuilder().add(
                Pos.class,
                Anim.class,
                Bounds.class,
                Renderable.class,
                Draggable.class,
                Blockade.class,
                Deletable.class,
                Clickable.class,
                Persistable.class,
                RenderMask.class,
                Bounds.class
        ).build(world);

        createInstancingButton("tool-resource-node", "resource-node", "resourceNode", 50);
        createInstancingButton("tool-techpoint", "techpoint", "techpoint", 50 + 40*1);

        createDrawingButton("tool-draw-duct",  50 + 10 + 40 * 2, 2, NavigationGridCalculationSystem.DUCT_COLOR, 80);
        createDrawingButton("tool-draw-floor",  50 + 10 + 40 * 3, 2, NavigationGridCalculationSystem.FLOOR_COLOR, 80);
        createDrawingButton("tool-draw-clear",  50 + 10 + 40 * 4, 2, Color.WHITE, 80);
        createDrawingButton("tool-draw-duct", 50 + 40 * 2, 6, NavigationGridCalculationSystem.DUCT_COLOR, 30);
        createDrawingButton("tool-draw-floor",  50 + 40 * 3, 6, NavigationGridCalculationSystem.FLOOR_COLOR, 30);
        createDrawingButton("tool-draw-clear", 50 + 40 * 4, 6, Color.WHITE, 30);

        addMaskTitle(RenderMask.Mask.BASIC, "Map overview", "Drag and place techpoints, rts, blockades and ducts.", "", "Rightclick to delete. Middleclick to cycle team on techpoints.");
        addMaskTitle(RenderMask.Mask.RT_PRESSURE, "RT head start", "highlight team that reaches RT first.", "Number signifies seconds head start.","Assign at least two techpoints to different teams.");
        addMaskTitle(RenderMask.Mask.RT_SYMMETRY_ALIEN, "Alien - RT run times", "Travel time in seconds between techpoints and RTs for aliens.", "", "");
        addMaskTitle(RenderMask.Mask.RT_SYMMETRY_MARINE, "Marine - RT run times", "Travel time in seconds between techpoints and RTs for marines.", "", "");
        addMaskTitle(RenderMask.Mask.PATHFIND_ALIEN, "Alien - all routes", "Travel time in seconds for aliens.", "", "");
        addMaskTitle(RenderMask.Mask.PATHFIND_MARINE, "Marine - all routes", "Travel time in seconds for marines.", "", "");
        addMaskTitle(RenderMask.Mask.TEAM_DOMAINS, "Presence", "Estimated presence of each team, strong to weak.", "Overlap indicates high encounter chance.", "");
    }

    LayerManager layerManager;

    private void createDrawingButton(final String toolIcon, int x, final int scale, final Color color, int y) {
        Entity button = createBasicButton(toolIcon, x, new ButtonListener() {
            @Override
            public void run() {

                Tool tool = new Tool(new ButtonListener() {

                    float lastX = 0;
                    float lastY = 0;

                    @Override
                    public void run() {

                        final Entity cursor = tagManager.getEntity("cursor");
                        if ( cursor != null ) {
                            Pos cursorPos = mPos.get(cursor);

                            // don't allow placement under a certain cursor position.
                            if ( cursorPos.y <= 100 ) return;

                            // avoid triggering twice for the same location.
                            if ( lastX == cursorPos.x && lastY == cursorPos.y )
                                return;

                            lastX = cursorPos.x;
                            lastY = cursorPos.y;

                            Layer raw = layerManager.getLayer("RAW", RenderMask.Mask.BASIC);
                            raw.pixmap.setColor(color);
                            raw.pixmap.fillCircle((int)cursorPos.x / NavigationGridManager.PATHING_CELL_SIZE, NavigationGridManager.GRID_HEIGHT - (int)cursorPos.y / NavigationGridManager.PATHING_CELL_SIZE, scale);

                            raw.invalidateTexture();
                            em.dispatch(new DrawEvent());
                        }
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                });
                tool.continuous = true;
                toolSystem.reset();
                Anim toolAnim = new Anim(toolIcon);
                toolAnim.scale = scale / 2f;
                new EntityBuilder(world).with(tool, new Pos(), new Renderable(1200), toolAnim).build();
            }

            @Override
            public boolean enabled() {
                return true;
            }
        }, y);

        mAnim.get(button).scale = scale / 2f;

        // make only visible when rendering.
    }

    ToolSystem toolSystem;
    AssetSystem assetSystem;
    EventManager em;

    private void createInstancingButton(final String toolIcon, final String animId, final String entityId, int x) {
        Entity button = createBasicButton(animId, x, new ButtonListener() {
            @Override
            public void run() {

                Tool tool = new Tool(new ButtonListener() {
                    @Override
                    public void run() {

                        final Entity cursor = tagManager.getEntity("cursor");
                        if ( cursor != null ) {
                            Pos cursorPos = mPos.get(cursor);

                            // don't allow placement under a certain cursor position.
                            if ( cursorPos.y <= 100 ) return;

                            Entity entity = createEntity(entityId, 0, 0, null);

                            Pos ePos = mPos.get(entity);
                            TextureRegion icon = assetSystem.get(mAnim.get(entity).id).getKeyFrame(0);
                            ePos.x = cursorPos.x - icon.getRegionWidth()/2;
                            ePos.y = cursorPos.y - icon.getRegionHeight()/2;

                            em.dispatch(new DragEvent(entity));
                        }
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                });
                tool.continuous = false;
                toolSystem.reset();
                new EntityBuilder(world).with(tool, new Pos(), new Renderable(1200), new Anim(toolIcon)).build();
            }

            @Override
            public boolean enabled() {
                return true;
            }
        }, 50);

        // make only visible when rendering.
    }

    private void addMaskTitle(RenderMask.Mask mask, String title, String subTitle1, String subTitle2, String help) {

        int y = 760;
        writeLabel(mask, title, 3, 50, y);
        if ( !subTitle1.isEmpty() ) writeLabel(mask, subTitle1, 2, 50, y -40);
        if ( !subTitle2.isEmpty() ) writeLabel(mask, subTitle2, 2, 50, y -60);
    }

    private void writeLabel(RenderMask.Mask mask, String title, int scale, int x, int y) {
        Label font = new Label(title);
        font.scale = scale;
        new EntityBuilder(world).with(
                new Renderable(1000),
                new net.mostlyoriginal.api.component.graphics.Color(0f,0f,0f,1f),
                new RenderMask(mask),
                new Pos(x, y),
                font)
                .build();
    }

    public Entity createBasicButton(String animId, int x, ButtonListener buttonListener, int y) {
        Anim anim = new Anim(animId);
        anim.scale = 2;
        return new EntityBuilder(world)
                .with(
                        new Pos(x, y),
                        new Bounds(32,32),
                        anim,
                        new Clickable(),
                        new Renderable(800),
                        new Button(animId,animId, animId, buttonListener)).build();
    }

    public Entity createEntity(String entity, int cx, int cy, MapProperties properties) {

        Entity e = null;
        switch (entity)
        {
            case "resourceNode":
                e = createResourceNode();
                break;
            case "techpoint":
                e = createTechpoint();
                break;
            case "duct":
                e = createDuct();
                break;
            case "wall":
                e = createWall();
                break;
            case "marine":
                e = createMarine();
                break;
            case "alien":
                e = createAlien();
                break;
        }

        if ( e != null && mPos.has(e))
        {
            Pos pos = mPos.get(e);
            pos.x = cx;
            pos.y = cy;
        }

        return e;
    }

    private Entity createMarine() {
        Entity marine = world.createEntity(this.marine);

        Anim anim = mAnim.get(marine);
        anim.id = "agent-marine";

        Renderable renderable = mRenderable.get(marine);
        renderable.layer = 999;

        TeamMember teamMember = mTeamMember.get(marine);
        teamMember.team = Team.MARINE;

        Bounds bounds = mBounds.get(marine);
        bounds.maxx = 16;
        bounds.maxy = 16;

        mRenderMask.get(marine).visible = EnumSet.allOf(RenderMask.Mask.class);

        return marine;
    }

    private Entity createAlien() {
        Entity alien = world.createEntity(this.alien);

        Anim anim = mAnim.get(alien);
        anim.id = "agent-alien";

        Renderable renderable = mRenderable.get(alien);
        renderable.layer = 1000;

        TeamMember teamMember = mTeamMember.get(alien);
        teamMember.team = Team.ALIEN;

        Bounds bounds = mBounds.get(alien);
        bounds.maxx = 16;
        bounds.maxy = 16;

        mRenderMask.get(alien).visible = EnumSet.allOf(RenderMask.Mask.class);

        return alien;
    }

    private Entity createResourceNode() {
        Entity node = world.createEntity(this.resourceNode);

        Anim anim = mAnim.get(node);
        anim.id = "resource-node";

        Bounds bounds = mBounds.get(node);
        bounds.maxx = 16;
        bounds.maxy = 16;

        mPersistable.get(node).saveId = "resourceNode";

        mRenderMask.get(node).visible = EnumSet.allOf(RenderMask.Mask.class);

        TeamMember teamMember = mTeamMember.get(node);
        teamMember.artUnaligned = "resource-node";
        teamMember.art.put(Team.ALIEN, "resource-node-alien");
        teamMember.art.put(Team.MARINE, "resource-node-marine");

        return node;
    }

    private Entity createDuct() {
        Entity node = world.createEntity(this.duct);

        Anim anim = mAnim.get(node);
        anim.id = "duct";

        Bounds bounds = mBounds.get(node);
        bounds.maxx=16;
        bounds.maxy=16;

        // ducts can be passed by aliens.
        Blockade blockade = mBlockade.get(node);
        blockade.passableBy = EnumSet.of(Team.ALIEN);

        mPersistable.get(node).saveId = "duct";

        mRenderMask.get(node).visible = EnumSet.of(RenderMask.Mask.BASIC);

        return node;
    }

    private Entity createWall() {
        Entity node = world.createEntity(this.duct);

        Anim anim = mAnim.get(node);
        anim.id = "wall";

        Bounds bounds = mBounds.get(node);
        bounds.maxx=16;
        bounds.maxy=16;

        // walls block both teams (null).
        Blockade blockade = mBlockade.get(node);
        blockade.passableBy = EnumSet.noneOf(Team.class);

        mPersistable.get(node).saveId = "wall";

        mRenderMask.get(node).visible = EnumSet.of(RenderMask.Mask.BASIC);

        return node;
    }

    private Entity createTechpoint() {
        Entity techpoint = world.createEntity(this.techpoint);

        Anim anim = mAnim.get(techpoint);
        anim.id = "techpoint";

        // since there is always a techpoint around we never use this for preferred route calculations.
        Routable routable = mRoutable.get(techpoint);
        routable.setIgnoreForPreferred(true);

        Bounds bounds = mBounds.get(techpoint);
        bounds.maxx = 16;
        bounds.maxy = 16;

        mPersistable.get(techpoint).saveId = "techpoint";

        mRenderMask.get(techpoint).visible = EnumSet.allOf(RenderMask.Mask.class);

        TeamMember teamMember = mTeamMember.get(techpoint);
        teamMember.artUnaligned = "techpoint";
        teamMember.art.put(Team.ALIEN, "techpoint-alien");
        teamMember.art.put(Team.MARINE, "techpoint-marine");

        return techpoint;
    }
}
