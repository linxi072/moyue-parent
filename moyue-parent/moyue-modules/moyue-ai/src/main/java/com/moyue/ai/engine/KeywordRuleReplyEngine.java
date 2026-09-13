package com.moyue.ai.engine;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 关键词规则回复引擎（内置默认实现）。
 * 覆盖平台高频问题：签到积分、兑换、打赏、书币充值、密码找回、审核时效、投诉建议等；
 * 未命中规则的输入统一走「转人工」兜底话术。
 * 有序遍历：先命中先返回（LinkedHashMap 保持声明顺序）。
 */
@Component
public class KeywordRuleReplyEngine implements AiReplyEngine {

    /** 规则表：关键词（任一命中即返回对应话术） */
    private static final Map<String[], String> RULES = new LinkedHashMap<>();

    static {
        RULES.put(new String[]{"签到", "打卡"},
                "每日签到可获得 10 积分哦～进入「积分商城」点击签到按钮即可，连续签到奖励更多，积分可用于兑换书币月卡、定制周边等好礼。");
        RULES.put(new String[]{"积分", "兑换"},
                "积分可以通过每日签到、参加活动等方式获得；在「积分商城」选择心仪商品，点「立即兑换」即可，库存与余额实时校验，兑换成功后可在「我的兑换」查看订单。");
        RULES.put(new String[]{"打赏", "赞赏", "支持作者"},
                "在作品详情或章节阅读页点击「打赏」，选择金额并用微信或支付宝支付，作者可获得 70% 分成，感谢你对创作者的支持！");
        RULES.put(new String[]{"充值", "书币", "会员"},
                "书币与会员功能正在灰度中，目前平台内消费以积分体系为主。上线后会第一时间在公告中通知，敬请期待～");
        RULES.put(new String[]{"密码", "找回", "忘记"},
                "当前为演示环境，账号由系统内置。正式版将支持手机号验证码找回密码；如有紧急问题请联系人工客服。");
        RULES.put(new String[]{"审核", "多久", "时效"},
                "章节与评论提交后会在 24 小时内完成审核；你可以在「消息中心」查看审核结果通知。超时未收到通知可在下方转人工咨询。");
        RULES.put(new String[]{"完结", "完本"},
                "作者可在「作品管理」中对作品发起完结申请，审核通过后作品状态将变更为「已完结」。读者可以继续阅读与打赏已完结作品。");
        RULES.put(new String[]{"周边", "商城", "实体", "包邮", "发货"},
                "「周边商城」提供墨阅定制周边：下单后 48 小时内发货，支持在「我的订单」中按订单号整单支付。库存不足的商品会提示补货哦。");
        RULES.put(new String[]{"投诉", "举报", "侵权"},
                "非常抱歉给你带来不好的体验！请提供作品名 / 用户名与具体问题描述，我们将在 1 个工作日内核实处理，感谢你的反馈。");
        RULES.put(new String[]{"你好", "在吗", "hi", "hello"},
                "你好呀～我是墨阅智能客服小墨，签到积分、兑换、打赏、周边商城等问题都可以问我！");
    }

    private static final String FALLBACK =
            "这个问题我还需要学习一下～已为你记录本次咨询，你可以换个说法再试试，或回复「人工」转接人工客服（工作日 10:00-18:00）。";

    @Override
    public String reply(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            return FALLBACK;
        }
        String normalized = userContent.toLowerCase();
        for (Map.Entry<String[], String> rule : RULES.entrySet()) {
            for (String keyword : rule.getKey()) {
                if (normalized.contains(keyword.toLowerCase())) {
                    return rule.getValue();
                }
            }
        }
        return FALLBACK;
    }

    @Override
    public String engineName() {
        return "keyword-rule-v1";
    }
}
