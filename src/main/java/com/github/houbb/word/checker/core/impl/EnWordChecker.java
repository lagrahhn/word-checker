package com.github.houbb.word.checker.core.impl;

import com.github.houbb.heaven.util.lang.StringUtil;
import com.github.houbb.heaven.util.util.CollectionUtil;
import com.github.houbb.word.checker.core.IWordChecker;
import com.github.houbb.word.checker.core.IWordCheckerContext;
import com.github.houbb.word.checker.exception.WordCheckRuntimeException;
import com.github.houbb.word.checker.support.dto.CandidateDto;
import com.github.houbb.word.checker.support.format.IWordFormat;
import com.github.houbb.word.checker.support.i18n.I18N;

import java.util.*;

/**
 * <p> 英文单词拼写检查 </p>
 *
 * <pre> Created: 2018-05-02 08:59  </pre>
 * <pre> Project: word-checker  </pre>
 *
 * @author houbinbin
 * @version 0.0.1
 * @since 0.0.1
 */
public final class EnWordChecker implements IWordChecker {

    /**
     * 构造器私有
     *
     * @since 0.0.1
     */
    private EnWordChecker() {
    }

    /**
     * 静态内部类，实现单例
     *
     * @since 0.0.2
     */
    private static class EnWordCheckerHolder {
        private static final EnWordChecker INSTANCE = new EnWordChecker();
    }

    /**
     * 获取一个单例
     *
     * @return 实例
     */
    public static EnWordChecker getInstance() {
        return EnWordCheckerHolder.INSTANCE;
    }

    @Override
    public boolean isCorrect(String word, final IWordCheckerContext context) {
        String formatWord = formatWord(word, context.wordFormat());
        return context.wordData().freqData().containsKey(formatWord);
    }

    @Override
    public final String correct(String word, final IWordCheckerContext context) {
        // 优化，如果拼写正确，则直接返回本身。
        if (isCorrect(word, context)) {
            return word;
        }

        return correctList(word, 1, context).get(0);
    }

    @Override
    public List<String> correctList(String word, int limit, final IWordCheckerContext context) {
        if (limit < 1) {
            throw new WordCheckRuntimeException(I18N.get("english_word_correct_limit_out_of_range"));
        }
        // 英文格式化
        final String formatWord = formatWord(word, context.wordFormat());

        // 所有的可能结果
        Map<Integer, Set<String>> editDistanceWordSetMap = buildEditDistanceWordSetMap(formatWord, context.maxEditDistance());
        // 所有的待选列表
        List<CandidateDto> candidateList = buildCandidateList(editDistanceWordSetMap, context);

        // fast-return
        if (CollectionUtil.isEmpty(candidateList)) {
            return Collections.singletonList(word);
        }

        // 正常处理
        return getCandidateList(candidateList, limit);
    }

    /**
     * 构建所有符合的待选列表
     *
     * @param map     map
     * @param context 上下文
     * @return 结果
     * @since 1.1.0
     */
    private List<CandidateDto> buildCandidateList(Map<Integer, Set<String>> map,
                                                  final IWordCheckerContext context) {
        final Map<String, Integer> wordDataMap = context.wordData().freqData();

        List<CandidateDto> resultList = new ArrayList<>();
        // 用于单词去重
        Set<String> allWordSet = new HashSet<>();
        for (Map.Entry<Integer, Set<String>> entry : map.entrySet()) {
            int distance = entry.getKey();
            Set<String> wordSet = entry.getValue();

            for (String word : wordSet) {
                if (allWordSet.contains(word)) {
                    continue;
                }

                if (!wordDataMap.containsKey(word)) {
                    continue;
                }

                // 加入
                allWordSet.add(word);

                // 处理
                CandidateDto candidateDto = CandidateDto.newInstance()
                        .count(wordDataMap.get(word))
                        .editDistance(distance)
                        .word(word);
                resultList.add(candidateDto);
            }
        }

        return resultList;
    }

    /**
     * 根据编辑距离，构建所有的可能性 map
     * <p>
     * key: 当前的编辑距离
     * value: 所有的可能值 set
     *
     * @param formatWord 格式化后的单词
     * @return 结果
     */
    private Map<Integer, Set<String>> buildEditDistanceWordSetMap(String formatWord,
                                                                  int maxEditDistance) {
        Map<Integer, Set<String>> map = new HashMap<>(maxEditDistance + 1);

        // 加入自己
        Set<String> words = new HashSet<>();
        words.add(formatWord);
        map.put(0, words);

        // 遍历处理
        for (int i = 1; i <= maxEditDistance; i++) {
            // 上一级的所有可能性
            Set<String> preLevelWords = map.get(i - 1);

            // 临时存放，避免一直变化
            // 其实有多少种可能，应该是上面的 size * 多少种可能性。避免扩容
            Set<String> allWordSet = new HashSet<>();

            for (String word : preLevelWords) {
                // 编辑距离为1的单词
                Set<String> otherWordSet = edits(word);
                allWordSet.addAll(otherWordSet);
            }

            // 加入到当前的 level
            map.put(i, allWordSet);
        }

        return map;
    }

    /**
     * 获取待选列表
     *
     * @param candidateDtos 列表
     * @param limit         限制
     * @return 截断后的列表
     * @since 0.0.1
     */
    private List<String> getCandidateList(List<CandidateDto> candidateDtos, final int limit) {
        List<String> result = new LinkedList<>();
        //1.排序
        Collections.sort(candidateDtos);

        int limitSize = Math.min(limit, candidateDtos.size());
        for (CandidateDto dto : candidateDtos) {
            if (result.size() >= limitSize) {
                break;
            }
            if (result.contains(dto.word())) {
                continue;
            }
            result.add(dto.word());
        }
        return result;
    }

    @Override
    public List<String> correctList(String word, final IWordCheckerContext context) {
        return correctList(word, Integer.MAX_VALUE, context);
    }

    // 编辑距离：是指将一个字符串转换为另一个字符串所需的最少编辑操作次数
    // 常见操作：插入一个字符、删除一个字符、替换一个字符
    // 在本函数中，操作有：删除一个字符，交换两个字符的位置，替换一个字符，插入一个字符

    /**
     * @param word：传入的单词
     * @return 返回该单词编辑距离为1的列表
     */
    public Set<String> edits(String word) {
        Set<String> resultSet = new HashSet<>();

        // 例如对于单词 hello，删除一个字符
        // 运行结果：
        //  ello
        //  hllo
        //  helo
        //  helo
        //  hell
        for (int i = 0; i < word.length(); ++i) {
            resultSet.add(word.substring(0, i) + word.substring(i + 1));
        }
        // 交换两个字符的位置
        // 运行结果
        //  ehllo
        //  hlelo
        //  hello
        //  helol
        for (int i = 0; i < word.length() - 1; ++i) {
            resultSet.add(word.substring(0, i) + word.substring(i + 1, i + 2) + word.substring(i, i + 1) + word.substring(i + 2));
        }
        // 运行结果  替换一个字符
        // aello	bello	cello	dello	eello	fello	gello	hello	iello	jello	kello	lello	mello	nello	oello	pello	qello	rello	sello	tello	uello	vello	wello	xello	yello	zello	hallo	hbllo	hcllo	hdllo	hello	hfllo	hgllo	hhllo	hillo	hjllo	hkllo	hlllo	hmllo	hnllo	hollo	hpllo	hqllo	hrllo	hsllo	htllo	hullo	hvllo	hwllo	hxllo	hyllo	hzllo	healo	heblo	heclo	hedlo	heelo	heflo	heglo	hehlo	heilo	hejlo	heklo	hello	hemlo	henlo	heolo	heplo	heqlo	herlo	heslo	hetlo	heulo	hevlo	hewlo	hexlo	heylo	hezlo	helao	helbo	helco	heldo	heleo	helfo	helgo	helho	helio	heljo	helko	hello	helmo	helno	heloo	helpo	helqo	helro	helso	helto	heluo	helvo	helwo	helxo	helyo	helzo	hella	hellb	hellc	helld	helle	hellf	hellg	hellh	helli	hellj	hellk	helll	hellm	helln	hello	hellp	hellq	hellr	hells	hellt	hellu	hellv	hellw	hellx	helly	hellz
        for (int i = 0; i < word.length(); ++i) {
            for (char c = 'a'; c <= 'z'; ++c) {
                resultSet.add(word.substring(0, i) + c + word.substring(i + 1));
            }
        }

        // 插入一个字符
        // 运行结果:
        // ahello	bhello	chello	dhello	ehello	fhello	ghello	hhello	ihello	jhello	khello	lhello	mhello	nhello	ohello	phello	qhello	rhello	shello	thello	uhello	vhello	whello	xhello	yhello	zhello	haello	hbello	hcello	hdello	heello	hfello	hgello	hhello	hiello	hjello	hkello	hlello	hmello	hnello	hoello	hpello	hqello	hrello	hsello	htello	huello	hvello	hwello	hxello	hyello	hzello	heallo	hebllo	hecllo	hedllo	heello	hefllo	hegllo	hehllo	heillo	hejllo	hekllo	helllo	hemllo	henllo	heollo	hepllo	heqllo	herllo	hesllo	hetllo	heullo	hevllo	hewllo	hexllo	heyllo	hezllo	helalo	helblo	helclo	heldlo	helelo	helflo	helglo	helhlo	helilo	heljlo	helklo	helllo	helmlo	helnlo	helolo	helplo	helqlo	helrlo	helslo	heltlo	helulo	helvlo	helwlo	helxlo	helylo	helzlo	hellao	hellbo	hellco	helldo	helleo	hellfo	hellgo	hellho	hellio	helljo	hellko	helllo	hellmo	hellno	helloo	hellpo	hellqo	hellro	hellso	hellto	helluo	hellvo	hellwo	hellxo	hellyo	hellzo	helloa	hellob	helloc	hellod	helloe	hellof	hellog	helloh	helloi	helloj	hellok	hellol	hellom	hellon	helloo	hellop	helloq	hellor	hellos	hellot	hellou	hellov	hellow	hellox	helloy	helloz
        for (int i = 0; i <= word.length(); ++i) {
            for (char c = 'a'; c <= 'z'; ++c) {
                resultSet.add(word.substring(0, i) + c + word.substring(i));
            }
        }

        return resultSet;
    }

    /**
     * 格式化单词
     *
     * @param original   原始信息
     * @param wordFormat 格式化实现
     * @return 结果
     * @since 0.0.3
     */
    private String formatWord(final String original, final IWordFormat wordFormat) {
        if (StringUtil.isEmptyTrim(original)) {
            return original;
        }

        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = original.toCharArray();

        for (char c : chars) {
            stringBuilder.append(wordFormat.format(c));
        }
        return stringBuilder.toString();
    }

}
