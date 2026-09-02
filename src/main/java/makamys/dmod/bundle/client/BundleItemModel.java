package makamys.dmod.bundle.client;

import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收纳袋决策树模型 provider —— 在标准 {@link ItemStateModel} 基础上注入
 * {@link SelectedItemNode} 自定义节点，动态渲染袋内选中条目。
 * <p>
 * JSON 中使用自定义语义类型 {@value #SELECTED_ITEM_TYPE} 表达袋内物品层
 * （贴近高版本 MC items/ 规范，无需占位模型文件）。加载流程：
 * <ol>
 *   <li>JSON 预处理：将 {@code dmod:selected_item} 临时替换为 CatFrame 可解析的
 *       {@code minecraft:model} 占位形式（仅供解析器通过，不产生真实模型引用）。</li>
 *   <li>解析后：递归遍历决策树，将占位 {@link ItemStateNode.ModelLeaf} 替换为
 *       {@link SelectedItemNode}，彻底消除占位路径。</li>
 *   <li>渲染时：{@link SelectedItemNode} 求值返回 {@value #SELECTED_ITEM_TYPE} 哨兵值，
 *       {@link #render} 识别后调用 {@link #renderSelectedItemLayer} 递归渲染袋内物品。</li>
 * </ol>
 * <p>
 * 模型注册为 persistent（见 {@link ModelRegistry#registerItemModel}），压制
 * items/ JSON 的自动注册是有意设计：决策树由本类自行 parseRoot 获得，纹理收集
 * 仍由 NamespaceLoadTask 独立完成，两者不冲突。
 * <p>
 * Decision-tree model provider for bundles: injects a {@link SelectedItemNode}
 * into the parsed tree to dynamically render the selected inner item.
 * The JSON uses a semantic type {@value #SELECTED_ITEM_TYPE} (aligned with
 * modern MC items/ conventions, no placeholder model file required).
 * At load time the custom type is temporarily rewritten so CatFrame's parser
 * can handle it, then immediately replaced with a real SelectedItemNode —
 * no placeholder path survives past parsing.
 */
public class BundleItemModel extends ItemStateModel {

    /**
     * JSON 中声明式自定义类型 / 渲染时求值哨兵值。
     * 语义："袋内选中物品层"，同时用作 SelectedItemNode 求值返回的标记路径。
     */
    public static final String SELECTED_ITEM_TYPE = "dmod:selected_item";

    /**
     * 解析阶段临时占位模型路径：仅供 CatFrame 反序列化器识别为 ModelLeaf，
     * 解析完成后立即由 {@link #replaceSelectedItems} 替换为 {@link SelectedItemNode}，
     * 不存在于最终决策树中。
     */
    private static final String PARSE_PLACEHOLDER = "dmod:__selected_item_placeholder__";

    private BundleItemModel(ItemStateNode rootNode) {
        super(rootNode);
    }

    /**
     * 从 {@code assets/{namespace}/items/{name}.json} 解析决策树并构造模型。
     * 与 CatFrame 的 items/ 自动注册共用同一份 JSON，互不冲突。
     * <p>
     * 解析流程：JSON 预处理（临时替换自定义类型）→ CatFrame 解析 →
     * 后处理（将占位 ModelLeaf 替换为 {@link SelectedItemNode}）。
     */
    public static BundleItemModel load(String namespace, String name) {
        String resource = "/assets/" + namespace + "/items/" + name + ".json";
        try (InputStream stream = BundleItemModel.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundle item state JSON: " + resource);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                // Step 1: temporarily rewrite dmod:selected_item so CatFrame parser can handle it
                rewriteCustomTypes(json);
                ItemStateRoot root = ItemStateNode.parseRootFull(json);
                if (root == null || root.model == null) {
                    throw new IllegalStateException("Failed to parse bundle item state: " + resource);
                }
                // Step 2: replace placeholder ModelLeaves with real SelectedItemNodes
                ItemStateNode processed = replaceSelectedItems(root.model);
                return new BundleItemModel(processed);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundle item state: " + resource, e);
        }
    }

    // ==================== JSON 预处理：临时占位替换 ====================

    /**
     * 递归遍历决策树 JSON，将 {@code {"type": "dmod:selected_item"}} 临时替换为
     * CatFrame 解析器可识别的 {@code {"type": "minecraft:model", "model": "<placeholder>"}} 形式。
     * <p>
     * 仅用于让解析器通过；解析后由 {@link #replaceSelectedItems} 替换为
     * {@link SelectedItemNode}，占位路径不会存在于最终决策树中。
     */
    private static void rewriteCustomTypes(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            JsonElement typeElem = obj.get("type");
            if (typeElem != null && typeElem.isJsonPrimitive()
                    && SELECTED_ITEM_TYPE.equals(typeElem.getAsString())) {
                obj.addProperty("type", "minecraft:model");
                obj.addProperty("model", PARSE_PLACEHOLDER);
                List<String> keysToRemove = new ArrayList<>();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    String key = entry.getKey();
                    if (!"type".equals(key) && !"model".equals(key)) {
                        keysToRemove.add(key);
                    }
                }
                for (String key : keysToRemove) {
                    obj.remove(key);
                }
            } else {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    rewriteCustomTypes(entry.getValue());
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                rewriteCustomTypes(child);
            }
        }
    }

    // ==================== 后处理：占位 ModelLeaf → SelectedItemNode ====================

    /**
     * 递归遍历已解析的决策树，将所有占位 {@link ItemStateNode.ModelLeaf}（model 等于
     * {@value #PARSE_PLACEHOLDER}）替换为 {@link SelectedItemNode}。
     * <p>
     * 返回替换后的节点；非占位节点保持原结构（容器节点创建新实例以容纳替换后的子节点）。
     */
    private static ItemStateNode replaceSelectedItems(ItemStateNode node) {
        if (node == null) return null;

        if (node instanceof ItemStateNode.ModelLeaf) {
            return PARSE_PLACEHOLDER.equals(((ItemStateNode.ModelLeaf) node).model)
                    ? new SelectedItemNode()
                    : node;
        }
        if (node instanceof ItemStateNode.ConditionNode) {
            ItemStateNode.ConditionNode cn = (ItemStateNode.ConditionNode) node;
            return new ItemStateNode.ConditionNode(
                    cn.property, replaceSelectedItems(cn.onTrue), replaceSelectedItems(cn.onFalse));
        }
        if (node instanceof ItemStateNode.CompositeNode) {
            ItemStateNode.CompositeNode cn = (ItemStateNode.CompositeNode) node;
            List<ItemStateNode> children = new ArrayList<>(cn.models.size());
            for (ItemStateNode child : cn.models) {
                children.add(replaceSelectedItems(child));
            }
            return new ItemStateNode.CompositeNode(children);
        }
        if (node instanceof ItemStateNode.SelectNode) {
            ItemStateNode.SelectNode sn = (ItemStateNode.SelectNode) node;
            List<ItemStateNode.SelectCase> cases = new ArrayList<>(sn.cases.size());
            for (ItemStateNode.SelectCase sc : sn.cases) {
                cases.add(new ItemStateNode.SelectCase(sc.when, replaceSelectedItems(sc.node)));
            }
            return new ItemStateNode.SelectNode(sn.property, cases, replaceSelectedItems(sn.fallback));
        }
        if (node instanceof ItemStateNode.RangeDispatchNode) {
            ItemStateNode.RangeDispatchNode rn = (ItemStateNode.RangeDispatchNode) node;
            List<ItemStateNode.ThresholdEntry> entries = new ArrayList<>(rn.entries.size());
            for (ItemStateNode.ThresholdEntry e : rn.entries) {
                entries.add(new ItemStateNode.ThresholdEntry(e.threshold, replaceSelectedItems(e.node)));
            }
            return new ItemStateNode.RangeDispatchNode(
                    rn.property, rn.scale, replaceSelectedItems(rn.fallback), entries);
        }
        // EmptyNode 等无需处理
        return node;
    }

    // ==================== 自定义节点：SelectedItemNode ====================

    /**
     * 袋内选中物品层节点 —— 求值时返回 {@value #SELECTED_ITEM_TYPE} 哨兵值，
     * 由 {@link BundleItemModel#render} 识别并渲染袋内首条目。
     * <p>
     * {@link #collectModelPaths} 不添加任何路径（无关联模型文件，无需纹理收集）。
     * <p>
     * Sentinel node for the inner-item layer: evaluates to
     * {@value #SELECTED_ITEM_TYPE}, which the render loop intercepts to
     * dynamically render the selected bundle entry. No model paths are
     * collected since there is no backing model file.
     */
    private static class SelectedItemNode extends ItemStateNode {
        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return EvalResult.single(SELECTED_ITEM_TYPE);
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            // No backing model file — nothing to collect
        }
    }

    // ==================== 渲染 ====================

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
            if (SELECTED_ITEM_TYPE.equals(path)) {
                // SelectedItemNode 哨兵值：动态渲染袋内选中条目
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
     * 渲染袋内选中的条目（袋口层叠的物品）。已知限制：选中条目无 CatFrame 模型时跳过
     * 该层（袋口不显示物品），不做原版渲染回退。
     */
    private void renderSelectedItemLayer(ItemStack stack, RenderPhase phase, @Nullable Matrix4d preTransform) {
        ItemStack selected = BundleContents.getSelectedStack(stack);
        if (selected == null) return;
        Item selectedItem = selected.getItem();
        if (selectedItem == null || !ModelRegistry.hasItemModel(selectedItem)) {
            // 选中条目未接入 CatFrame 模型系统 → 跳过该层（袋口不显示物品）
            return;
        }
        // 防嵌套递归：选中条目注册的模型就是本模型（即同一物品套自己）时跳过。
        // 重量上限（4/64 每层）天然限制嵌套深度，此处再兜底。
        if (ModelRegistry.getRegisteredItemModel(selectedItem) == this) {
            return;
        }
        ModelRegistry.getRegisteredItemModel(selectedItem).render(selected, phase, preTransform);
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
