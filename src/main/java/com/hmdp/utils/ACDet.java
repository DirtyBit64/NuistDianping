package com.hmdp.utils;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.hmdp.constant.BlogConstants;
import com.hmdp.constant.SystemConstants;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 多模匹配算法，AC自动机
 * 给出一个字符串，匹配多个敏感词
 * demo:
 * 敏感词库    he say her shr she
 * 被检测字符  sherhsay
 * 检测结果    she her he say
 */
@Slf4j
@Component
public class ACDet {

    // 检测根节点
    private static ACNode detNode;

    public ACDet(){
        build();
    }

    public static void build(){
        detNode = new ACNode('-');
        detNode.failNode = null;
        // 从词库读取敏感词，构建自动机
        Resource resource = new ClassPathResource(SystemConstants.SENSITIVE_FILE_PATH);
        try {
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String word;
            while ((word= reader.readLine()) != null) {
                ACNode.insert(detNode, word);
            }
            ACNode.buildFailPoint(detNode);
            log.info("AC自动机构建完成");
        } catch (IOException e) {
            log.error("AC自动机构建失败,", e);
        }
    }

    public String detect(String word){
        String[] res = ACNode.query(detNode, word);
        if (res == null){
            return "";
        }
        // 返回敏感词
        return String.join(",", res);
    }

    @Data
    @NoArgsConstructor
    public static class ACNode {
        Character am;
        // 子节点
        Map<Character, ACNode> children = new HashMap<>();
        ACNode failNode;
        // 存储匹配到的敏感字符长度
        List<Integer> wordLength = new ArrayList<>();
        // 是否是结束字符
        private boolean endOfWord;

        public ACNode(Character am) {
            this.am = am;
        }

        public String toString() {
            return "ACNode{" +
                    "am=" + am + "," +
                    "children=" + children +
                    ",wordLength=" + wordLength +
                    '}';
        }


        // 构建字典树
        public static void insert(ACNode root, String s) {
            ACNode temp = root;
            char[] chars = s.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                if (!temp.children.containsKey(chars[i])) {
                    temp.children.put(chars[i], new ACNode(chars[i]));
                }
                temp = temp.children.get(chars[i]);
                // 如果是最后一个字符,则设置为结束字符
                if (i == chars.length - 1) {
                    temp.setEndOfWord(true);
                    temp.getWordLength().add(chars.length);
                }
            }
        }

        // 构建失败指针
        public static void buildFailPoint(ACNode root) {
            // 第一层的失败指针都是执行root,直接让第一层进入队列,方便 BFS
            Queue<ACNode> queue = new LinkedList<>();
            Map<Character, ACNode> childrens = root.getChildren();
            for (ACNode acNode : childrens.values()) {
                queue.offer(acNode);
                acNode.setFailNode(root);
            }
            // 构建剩余节点的失败指针,按层次遍历
            while (!queue.isEmpty()) {
                ACNode pnode = queue.poll();
                childrens = pnode.getChildren();
                Set<Map.Entry<Character, ACNode>> entries = childrens.entrySet();
                for (Map.Entry<Character, ACNode> entry : entries) {
                    // 当前节点的字符
                    Character key = entry.getKey();
                    ACNode cnode = entry.getValue();
                    // 如果当前节点的父节点的fail指针指向的节点下存在与当前节点一样的子节点，则当前节点的fail指针指向该子节点，否则指向root节点
                    if (pnode.failNode.children.containsKey(key)) {
                        cnode.setFailNode(pnode.failNode.children.get(key));
                    } else {
                        cnode.setFailNode(root);
                    }
                    // 如果当前节点的失败节点的wordLength不为空，则将当前节点的失败节点wordLength 合并到到当前节点的wordLength中
                    if (!CollectionUtils.isEmpty(cnode.failNode.wordLength)) {
                        cnode.getWordLength().addAll(cnode.failNode.wordLength);
                    }
                    queue.offer(cnode);
                }
            }

        }

        public static String[] query(ACNode root, String s) {
            ACNode temp = root;
            char[] chars = s.toCharArray();
            for (int i = 0; i < s.length(); i++) {
                // 如果这个字符串在当前节点的孩子节点找不到，且当前节点的fail指针不是null,则去失败指针去查找
                while (!temp.getChildren().containsKey(chars[i]) && temp.failNode != null) {
                    temp = temp.failNode;
                }
                // 如果当前节点有这个字符，则将temp替换为下面的孩子节点
                if (temp.getChildren().containsKey(chars[i])) {
                    temp = temp.getChildren().get(chars[i]);
                } else {
                    // 如果temp的failNode==null,则为root节点
                    continue;
                }
                // 如果检测到节点是结束字符，则将匹配到的敏感字符打印，并返回
                if (temp.isEndOfWord()) {
                    return handle(temp, s, i);
                }
            }
            return null;
        }

        public static String[] handle(ACNode node, String word, int curPoint) {
            List<String> res = new ArrayList<>();
            for (Integer wordLen : node.wordLength) {
                int start = curPoint - wordLen + 1;
                String mathStr = word.substring(start, curPoint + 1);
                res.add(mathStr);
            }
            return res.toArray(new String[]{});
        }
    }
}