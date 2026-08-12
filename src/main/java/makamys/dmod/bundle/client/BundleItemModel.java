package makamys.dmod.bundle.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.item.EvalResult;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateRoot;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import makamys.dmod.bundle.BundleContents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 收纳袋决策树模型 provider —— 在标准 {@link ItemStateModel} 基础上拦截
 * "袋内选中物品"特殊模型路径，动态渲染袋内首条目。
 * <p>
 * CatFrame 的 items/ JSON 决策树不支持自定义 model type（{@code minecraft:special}
 * 与未知 type 直接抛 {@code JsonParseException}），因此袋内物品层用特殊模型路径
 * {@value #BUNDLE_SELECTED_ITEM_MARKER} 标记（资源侧为空模型占位），由本类在
 * {@link #render} 中拦截并替换为对袋内物品自身 CatFrame 模型的递归渲染。
 * <p>
 * 模型注册为 persistent（见 {@link ModelRegistry#registerItemModel}），压制
 * items/ JSON 的自动注册是有意设计：决策树由本类自行 parseRoot 获得，纹理收集
 * 仍由 NamespaceLoadTask 独立完成，两者不冲突。
 * <p>
 * Decision-tree model provider for bundles: intercepts the special
 * {@value #BUNDLE_SELECTED_ITEM_MARKER} model path and dynamically renders the
 * first bundled stack, since CatFrame's JSON decision trees cannot express
 * arbitrary item layers.
 */
public class BundleItemModel extends ItemStateModel {

    /** 决策树中代表"袋内选中物品"层的特殊模型路径（资源侧为空模型占位）。 */
    public static final String BUNDLE_SELECTED_ITEM_MARKER = "dmod:item/bundle_selected_item";

    private BundleItemModel(ItemStateNode rootNode) {
        super(rootNode);
    }

    /**
     * 从 {@code assets/{namespace}/items/{name}.json} 解析决策树并构造模型。
     * 与 CatFrame 的 items/ 自动注册共用同一份 JSON，互不冲突。
     */
    public static BundleItemModel load(String namespace, String name) {
        String resource = "/assets/" + namespace + "/items/" + name + ".json";
        try (InputStream stream = BundleItemModel.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundle item state JSON: " + resource);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                ItemStateRoot root = ItemStateNode.parseRootFull(json);
                if (root == null || root.model == null) {
                    throw new IllegalStateException("Failed to parse bundle item state: " + resource);
                }
                return new BundleItemModel(root.model);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundle item state: " + resource, e);
        }
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase, @Nullable Matrix4d preTransform) {
        // 1. 构建运行时属性集
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);

        // 2. 递归求值决策树，得到 EvalResult
        EvalResult result = getRootNode().evaluate(props);
        if (result.isEmpty()) {
            // 决策树未找到任何模型 → fallback 到 builtin/missing（与 ItemStateModel 一致）
            String cacheKey = BakedModelCache.buildKey("builtin/missing", 0, 0);
            BlockStateModelPart missing = BakedModelCache.INSTANCE.get(cacheKey);
            if (missing != null && !missing.isEmpty()) {
                UniformRenderPipeline.renderItemQuads(missing, stack, phase,
                        null, 0, 0, 0, null, preTransform);
            }
            return;
        }

        // 3. 遍历所有选中的模型路径，逐个渲染（composite 时多模型分层）
        for (String path : result.getModels()) {
            if (BUNDLE_SELECTED_ITEM_MARKER.equals(path)) {
                // 特殊层：动态渲染袋内首条目（绕开 JSON 决策树不支持自定义 model type 的限制）
                renderSelectedItemLayer(stack, phase, preTransform);
                continue;
            }
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) continue;

            // transformation：命中 ModelLeaf 声明的物品模型渲染变换（可选，默认单位变换）
            Matrix4d transformation = findTransformationForModel(getRootNode(), path);
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, preTransform, transformation);
        }
    }

    /**
     * 渲染袋内首条目（袋口层叠的物品）。已知限制：首条目无 CatFrame 模型时跳过该层
     * （袋口不显示物品），不做原版渲染回退。
     */
    private void renderSelectedItemLayer(ItemStack stack, RenderPhase phase, @Nullable Matrix4d preTransform) {
        ItemStack first = BundleContents.getFirstItem(stack);
        if (first == null) return;
        Item firstItem = first.getItem();
        if (firstItem == null || !ModelRegistry.hasItemModel(firstItem)) {
            // 首条目未接入 CatFrame 模型系统 → 跳过该层（袋口不显示物品）
            return;
        }
        // 防嵌套递归：首条目注册的模型就是本模型（即同一物品套自己）时跳过。
        // 重量上限（4/64 每层）天然限制嵌套深度，此处再兜底。
        if (ModelRegistry.getRegisteredItemModel(firstItem) == this) {
            return;
        }
        ModelRegistry.getRegisteredItemModel(firstItem).render(first, phase, preTransform);
    }

    /**
     * 在决策树中查找指定模型路径的 ModelLeaf，提取其 transformation 矩阵。
     * 与 {@link ItemStateModel} 内部实现一致（该实现为 private，无法复用）。
     */
    @Nullable
    private static Matrix4d findTransformationForModel(ItemStateNode node, String path) {
        if (node == null) return null;
        if (node instanceof ItemStateNode.ModelLeaf) {
            ItemStateNode.ModelLeaf leaf = (ItemStateNode.ModelLeaf) node;
            return path.equals(leaf.model) ? leaf.transformation : null;
        }
        if (node instanceof ItemStateNode.ConditionNode) {
            ItemStateNode.ConditionNode cn = (ItemStateNode.ConditionNode) node;
            Matrix4d r = findTransformationForModel(cn.onTrue, path);
            if (r != null) return r;
            return findTransformationForModel(cn.onFalse, path);
        }
        if (node instanceof ItemStateNode.RangeDispatchNode) {
            ItemStateNode.RangeDispatchNode rn = (ItemStateNode.RangeDispatchNode) node;
            Matrix4d r = findTransformationForModel(rn.fallback, path);
            if (r != null) return r;
            for (ItemStateNode.ThresholdEntry e : rn.entries) {
                r = findTransformationForModel(e.node, path);
                if (r != null) return r;
            }
            return null;
        }
        if (node instanceof ItemStateNode.SelectNode) {
            ItemStateNode.SelectNode sn = (ItemStateNode.SelectNode) node;
            Matrix4d r = findTransformationForModel(sn.fallback, path);
            if (r != null) return r;
            for (ItemStateNode.SelectCase sc : sn.cases) {
                r = findTransformationForModel(sc.node, path);
                if (r != null) return r;
            }
            return null;
        }
        if (node instanceof ItemStateNode.CompositeNode) {
            ItemStateNode.CompositeNode cn = (ItemStateNode.CompositeNode) node;
            for (ItemStateNode child : cn.models) {
                Matrix4d r = findTransformationForModel(child, path);
                if (r != null) return r;
            }
            return null;
        }
        return null;
    }
}
