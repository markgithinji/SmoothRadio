package com.smoothradio.radio.feature.discover;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.smoothradio.radio.MainActivity;
import com.smoothradio.radio.R;
import com.smoothradio.radio.model.Category;
import com.smoothradio.radio.model.RadioStation;

import java.util.ArrayList;
import java.util.List;


public class DiscoverFragment extends Fragment {
    RecyclerView rvDiscover;
    LinearLayoutManager LLM;
    DiscoverRecyclerViewAdapter discoverRecyclerViewAdapter;
    MainActivity mainActivity;
    public static List<String> linksFromTxt = new ArrayList<>();

    List<RadioStation> CategoryHotRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryMixesRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryKikuyuRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryMaasaiRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryKalenjinRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryLuoRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryKisiiRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryLuhyaRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryKambaRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryMeruRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryTalksRadioStationList = new ArrayList<>();
    List<RadioStation> CategorySportRadioStationList = new ArrayList<>();
    List<RadioStation> CategorySdaRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryChristianContemporaryRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryPraiseWorshipRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryIslamRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryCoastalRadioStationList = new ArrayList<>();
    List<RadioStation> CategoryAsianStationList = new ArrayList<>();
    List<RadioStation> CategoryEdmStationList = new ArrayList<>();
    List<RadioStation> CategoryCatholicStationList = new ArrayList<>();
    List<RadioStation> CategoryPokotStationList = new ArrayList<>();
    List<RadioStation> CategoryReggaeStationList = new ArrayList<>();
    List<RadioStation> CategoryEthiopianStationList = new ArrayList<>();



    List<Category> CategoryList= new ArrayList<>();

    public DiscoverFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity= (MainActivity) getActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(linksFromTxt.size()==0)
        {
        linksFromTxt.clear();
        linksFromTxt.addAll(mainActivity.linksFromTxt); }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        linksFromTxt.clear();
        linksFromTxt.addAll(MainActivity.linksFromTxt);
        if (linksFromTxt.size()>0)
        { addItems();
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_discover, container, false);
        rvDiscover = root.findViewById(R.id.rvDiscover);
        discoverRecyclerViewAdapter= new DiscoverRecyclerViewAdapter(CategoryList);
        rvDiscover.setAdapter(discoverRecyclerViewAdapter);
        LLM = new LinearLayoutManager(root.getContext());
        LLM.setOrientation(RecyclerView.VERTICAL);
        rvDiscover.setLayoutManager(LLM);
        rvDiscover.setHasFixedSize(true);
        return root;
        }

        //If Something goes wrong
        View root = inflater.inflate(R.layout.discover_fragment_failed_load, container, false);
        Button btnRestart = root.findViewById(R.id.btnRestart);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(),MainActivity.class);

                mainActivity.finish();
                getActivity().finish();
                startActivity(intent);

                linksFromTxt.addAll(MainActivity.linksFromTxt);
            }
        });
        return root;
    }
    void addItems()
    {
        //HOT
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.soundcityradiologo,"SOUNDCITY RADIO","88.5","NAIROBI",linksFromTxt.get(1),1));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.nrg_hiphop_logo, "NRG HIPHOP", "--.--", "NAIROBI", linksFromTxt.get(211),211));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.radio47logo, "RADIO 47", "103.0", "NAIROBI", linksFromTxt.get(228), 228));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.rogueradiologo,"ROGUE RADIO","--.--","NAIROBI",linksFromTxt.get(26),26));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.radio254logo,"RADIO 254","--.--","NAIROBI",linksFromTxt.get(129),129));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.dapstreamradiologo,"DAPSTREAM RADIO","--.--","NAIROBI",linksFromTxt.get(188),188));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.hypemagnetradiologo,"HYPEMAGNET RADIO","--.--","NAIROBI",linksFromTxt.get(153),153));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.flamingoradiologo,"FLAMINGO FM","93.7","NAKURU",linksFromTxt.get(62),62));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.campusradiologo,"CAMPUS RADIO","--.--","NAIROBI",linksFromTxt.get(91),91));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.hiphopginlogo,"HIPHOPGIN","--.--","NAIROBI",linksFromTxt.get(74),74));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.hiphopdailylogo,"HIPHOP DAILY","97.5","NAIROBI",linksFromTxt.get(75),75));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.smashjamradiologo,"SMASH JAM RADIO","99.0","NAKURU",linksFromTxt.get(104),104));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.popoteradiologo,"POPOTE RADIO","--.--","NAIROBI",linksFromTxt.get(197),197));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.capitalfmlogo,"CAPITAL FM","98.4","NAIROBI",linksFromTxt.get(54),54));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.tracefmlogo,"TRACE FM","95.3","NAIROBI",linksFromTxt.get(30),30));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.relaxradiologo,"RELAX RADIO","103.0","NAIROBI",linksFromTxt.get(103),103));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.optimumradiologo,"OPTIMUM RADIO","--.--","NAIROBI",linksFromTxt.get(165),165));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.touchradiologo,"TOUCH RADIO","--.--","NAIROBI",linksFromTxt.get(184),184));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.nworksradiologo,"NWORKS RADIO","--.--","NAIROBI",linksFromTxt.get(193),193));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.badidearadiologo,"BAD IDEA RADIO","--.--","NAIROBI",linksFromTxt.get(199),199));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.icedfmlogo,"ICED RADIO","--.--","NAIROBI",linksFromTxt.get(138),138));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.geeradiologo,"GEE RADIO","--.--","NAIROBI",linksFromTxt.get(139),139));
        CategoryHotRadioStationList.add(new RadioStation(R.drawable.kufurahiafmlogo,"KUFURAHIA FM","--.--","NAIROBI",linksFromTxt.get(142),142));

        //MIXES
        CategoryMixesRadioStationList.add(new RadioStation(R.drawable.popoteradiologo,"POPOTE RADIO","--.--","NAIROBI",linksFromTxt.get(197),97));
        CategoryMixesRadioStationList.add(new RadioStation(R.drawable.mixxradio560logo,"560 MIXX RADIO","560kHz","NAIROBI",linksFromTxt.get(109),109));
        CategoryMixesRadioStationList.add(new RadioStation(R.drawable.radio254logo,"RADIO 254","--.--","NAIROBI",linksFromTxt.get(129),129));
        CategoryMixesRadioStationList.add(new RadioStation(R.drawable.nrg_mixology_logo, "NRG MIXOLOGY", "--.--", "NAIROBI", linksFromTxt.get(215),215));
        CategoryMixesRadioStationList.add(new RadioStation(R.drawable.freshfmlogo,"FRESH FM","--.--","NAIROBI",linksFromTxt.get(152),152));

        //KIKUYU
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.kikuyudiasporaradiologo,"KIKUYU DIASPORA RADIO","--.--","ALABAMA",linksFromTxt.get(38),38));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.muratafmlogo,"MURATA FM","98.2","ELDORET",linksFromTxt.get(90),90));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.theophilusfmlogo, "THEOPHILUS FM", "--.--", "NAIROBI", linksFromTxt.get(221),221));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.kayufmlogo, "KAYU FM", "91.0", "NAIROBI", linksFromTxt.get(226),226));
        // CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.njatafmlogo,"NJATA FM","97.2","NAIROBI",linksFromTxt.get(206)));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.muthingifmlogo, "MUTHINGI FM", "--.--", "NAIROBI", linksFromTxt.get(225),225));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.aipcalogo,"AIPCA RADIO","--.--","NAIROBI",linksFromTxt.get(145),145));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.gukenafmlogo,"GUKENA FM","92.8","NAIROBI",linksFromTxt.get(41),41));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.mugambowamugikuyulogo,"MUGAMBO WA MUGIKUYU","--.--","NAIROBI",linksFromTxt.get(94),94));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.centralfmlogo,"CENTRAL FM","97.1","NANYUKI",linksFromTxt.get(112),112));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.inooro,"INOORO FM","98.9","NAIROBI",linksFromTxt.get(4),4));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.corofmlogo,"CORO FM","99.5","NAIROBI",linksFromTxt.get(10),10));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.kameme,"KAMEME FM","101.1","NAIROBI",linksFromTxt.get(11),11));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.utheriradiologo,"UTHERI RADIO","106.2","NAIROBI",linksFromTxt.get(93),93));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.kigoocofmlogo,"KIGOOCO FM","98.6","NAIROBI",linksFromTxt.get(32),32));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.ruimbofmlogo,"RUIMBO FM","--.--","NAIROBI",linksFromTxt.get(177),177));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.tognofm,"TOGNO FM","--.--","NAIROBI",linksFromTxt.get(181),181));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.gituambafmlogo,"GITUAMBA FM","98.2MHz","NAIROBI",linksFromTxt.get(168),168));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.uigithaniofmlogo,"UIGITHANIO FM","--.--","NAIROBI",linksFromTxt.get(185),185));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.ksmradiologo,"KSM RADIO","--.--","NAIROBI",linksFromTxt.get(187),187));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.kihootofmlogo,"KIHOOTO FM","91.2","NAIROBI",linksFromTxt.get(77),77));
        CategoryKikuyuRadioStationList.add(new RadioStation(R.drawable.utuuroradiologo,"UTUURO RADIO","--.--","NAIROBI",linksFromTxt.get(99),99));


        //MAASAI
        CategoryMaasaiRadioStationList.add(new RadioStation(R.drawable.eretofmlogo,"ERETO FM","--.--","NYERI",linksFromTxt.get(122),122));
        CategoryMaasaiRadioStationList.add(new RadioStation(R.drawable.serianfmlogo,"SERIAN FM","--.--","NAIROBI",linksFromTxt.get(194),194));
        CategoryMaasaiRadioStationList.add(new RadioStation(R.drawable.mayianfmlogo,"MAYIAN FM","100.7","NAROK",linksFromTxt.get(64),64));
        CategoryMaasaiRadioStationList.add(new RadioStation(R.drawable.sidaifmlogo,"SIDAI FM","103.5","NAROK",linksFromTxt.get(83),83));
        CategoryMaasaiRadioStationList.add(new RadioStation(R.drawable.nosimfmlogo, "NOSIM FM", "90.5", "NAIROBI", linksFromTxt.get(219),219));

        //KALENJIN
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.chamgeilogo,"CHAMGEI FM","95.0","NAIROBI",linksFromTxt.get(19),19));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.taachfmlogo,"TAACH FM","95.1","ELDORET",linksFromTxt.get(39),39));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.berurfmlogo, "BERUR FM", "96.7", "NAIROBI", linksFromTxt.get(227),227));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.kassfmlogo,"KASS FM","89.1","NAIROBI",linksFromTxt.get(42),42));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.emoofmlogo,"EMOO FM","104.2","NAIROBI",linksFromTxt.get(66),66));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.bikapkoretradiologo,"BIKAPKORET RADIO","98.2","ELDORET",linksFromTxt.get(89),89));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.congasisfmlogo,"CONG'ASIS FM","88.9","NAIROBI",linksFromTxt.get(101),101));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.kitwekradiologo,"KITWEK RADIO","92.9","NAIROBI",linksFromTxt.get(114),114));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.tulwoobkoonyradiologo,"TULWOOBKOONY RADIO","--.--","NAIROBI",linksFromTxt.get(140),140));
        CategoryKalenjinRadioStationList.add(new RadioStation(R.drawable.konyonfmlogo,"KONYON FM","--.--","KERICHO",linksFromTxt.get(157),157));

        //TALKS
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.radiojambologo,"RADIO JAMBO","97.5","NAIROBI",linksFromTxt.get(9),9));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.doctorsexplainlogo,"DOCTORS EXPLAIN","--.--","NAIROBI",linksFromTxt.get(161),161));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.tonziradiologo,"TONZI RADIO","--.--","",linksFromTxt.get(131),131));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.tulizafmlogo,"TULIZA FM","94.2","MERU",linksFromTxt.get(76),76));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.longaradiologo,"LONGA RADIO","--.--","NAIROBI",linksFromTxt.get(204),204));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.radiobarazalogo,"RADIO BARAZA","--.--","NAIROBI",linksFromTxt.get(166),166));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.tandazafmlogo,"TANDAZA FM","103.7","BUNGOMA",linksFromTxt.get(108),108));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.radiofahamulogo,"RADIO FAHAMU","--.--","NAIROBI",linksFromTxt.get(127),127));
        CategoryTalksRadioStationList.add(new RadioStation(R.drawable.radiosimbalogo,"RADIO SIMBA","93.1","BUNGOMA",linksFromTxt.get(85),85));

        //SPORT
        CategorySportRadioStationList.add(new RadioStation(R.drawable.mwanaspotifmlogo,"MWANASPORTI FM","--.--","NAIROBI",linksFromTxt.get(183),183));
        CategorySportRadioStationList.add(new RadioStation(R.drawable.radiojambologo,"RADIO JAMBO","97.5","NAIROBI",linksFromTxt.get(9),9));

        //SDA
        CategorySdaRadioStationList.add(new RadioStation(R.drawable.angeladventistlogo,"ADVENTIST ANGELS","90.0","KISII",linksFromTxt.get(98),98));
        CategorySdaRadioStationList.add(new RadioStation(R.drawable.radioneemalogo,"RADIO NEEMA","--.--","NAIROBI",linksFromTxt.get(84),84));
        CategorySdaRadioStationList.add(new RadioStation(R.drawable.lightfmlogo,"LIGHT FM","--.--","NAIROBI",linksFromTxt.get(137),137));
        CategorySdaRadioStationList.add(new RadioStation(R.drawable.safinaradiologo,"SAFINA RADIO","--.--","NAIROBI",linksFromTxt.get(178),178));

        //LUO
        CategoryLuoRadioStationList.add(new RadioStation(R.drawable.ramogilogo,"RAMOGI FM","107.1","NAIROBI",linksFromTxt.get(15),15));
        CategoryLuoRadioStationList.add(new RadioStation(R.drawable.osiepefmlogo, "OSIEPE FM", "96.8", "SIAYA", linksFromTxt.get(220),220));
        CategoryLuoRadioStationList.add(new RadioStation(R.drawable.mayiengaradiologo,"MAYIENGA FM","93.5","BUNGOMA",linksFromTxt.get(113),113));
        CategoryLuoRadioStationList.add(new RadioStation(R.drawable.maneneradiologo,"MANENE RADIO","--.--","KISUMU",linksFromTxt.get(100),100));
        CategoryLuoRadioStationList.add(new RadioStation(R.drawable.radiomikayilogo,"RADIO MIKAYI","88.8","HOMABAY",linksFromTxt.get(156),156));

        //KISII
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.egesalogo,"EGESA FM","98.6","NAIROBI",linksFromTxt.get(16),16));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.mintofmlogo,"MINTO FM","101.7","NAIROBI",linksFromTxt.get(88),88));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.kisiifmlogo,"KISII FM","94.1","NAIROBI",linksFromTxt.get(78),78));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.seitofmlogo,"SEITO FM","100.3","NAIROBI",linksFromTxt.get(80),80));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.radiovunalogo,"RADIO VUNA","102.0","KISII",linksFromTxt.get(205),205));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.getembefmlogo,"GETEMBE FM","102.7","KISII",linksFromTxt.get(203),203));
        CategoryKisiiRadioStationList.add(new RadioStation(R.drawable.inkafmlogo,"INKA FM","93.7","KISII",linksFromTxt.get(204),204));

        //LUGHYA
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.mulembelogo,"MULEMBE FM","97.9","NAIROBI",linksFromTxt.get(17),17));
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.lubaofmlogo,"LUBAO FM","102.2","KAKAMEGA",linksFromTxt.get(92),92));
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.radiosimbalogo,"RADIO SIMBA","93.1","BUNGOMA",linksFromTxt.get(85),85));
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.westfmlogo,"WEST FM","94.9","NAIROBI",linksFromTxt.get(86),86));
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.mumbofmlogo,"MUMBO FM","90.2","BUNGOMA",linksFromTxt.get(107),107));
        CategoryLuhyaRadioStationList.add(new RadioStation(R.drawable.ingofmlogo,"INGO FM","100.5","NAIROBI",linksFromTxt.get(116),116));

        //KAMBA
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.musyilogo,"MUSYI FM","102.2","NAIROBI",linksFromTxt.get(18),18));
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.countyfmlogo, "COUNTY FM", "90.3", "KITUI", linksFromTxt.get(82),82));
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.mutongoifmlogo, "MUTONGOI FM", "103.3", "KITUI", linksFromTxt.get(223),223));
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.athianilogo,"ATHIANI FM","99.2","NAIROBI",linksFromTxt.get(53),53));
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.mbaitufmlogo,"MBAITU FM","92.5","MACHAKOS",linksFromTxt.get(71),71));
        CategoryKambaRadioStationList.add(new RadioStation(R.drawable.mwakifmlogo,"MWAKI FM","--.--","NAIROBI",linksFromTxt.get(95),95));

        //MERU
        CategoryMeruRadioStationList.add(new RadioStation(R.drawable.muugalogo,"MUUGA FM","94.2","NAIROBI",linksFromTxt.get(20),20));
        CategoryMeruRadioStationList.add(new RadioStation(R.drawable.merufmlogo,"MERU FM","100.3","MERU",linksFromTxt.get(65),65));
        CategoryMeruRadioStationList.add(new RadioStation(R.drawable.getufmlogo,"GETU RADIO","87.6","MERU",linksFromTxt.get(105),105));
        CategoryMeruRadioStationList.add(new RadioStation(R.drawable.mwagofmlogo, "MWANGO FM", "97.5", "NAIROBI", linksFromTxt.get(218),218));

        //CHRISTIAN CONTEMPORARY
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.kubambaradiologo,"KUBAMBA RADIO","--.--","NAIROBI",linksFromTxt.get(25),25));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.daradiologo,"DA RADIO","--.--","NAIROBI",linksFromTxt.get(169),169));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.beatlocklogo,"BEATLOCK RADIO","--.--","NAIROBI",linksFromTxt.get(55),55));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.familyradiologo,"FAMILY RADIO","103.9","NAIROBI",linksFromTxt.get(28),28));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.pearlxtralogo,"PEARL XTRA FM","--.--","NAIROBI",linksFromTxt.get(56),56));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.abundanceradio,"ABUNDANCE RADIO","--.--","NAIROBI",linksFromTxt.get(144),144));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.truthfmlogo,"TRUTH FM","90.7","NAIROBI",linksFromTxt.get(27),27));
        CategoryChristianContemporaryRadioStationList.add(new RadioStation(R.drawable.bloomradiologo,"BLOOM RADIO","--.--","NAIROBI",linksFromTxt.get(119),119));

        //CHRISTIAN PRAISE AND WORSHIP
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.lulufmlogo,"LULU FM","91.0","MOMBASA",linksFromTxt.get(58),58));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.truthfmlogo,"TRUTH FM","90.7","NAIROBI",linksFromTxt.get(27),27));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.nrg_gospel_logo, "NRG GOSPEL", "--.--", "NAIROBI", linksFromTxt.get(213),213));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.gfnradiologo, "GFN FM", "90.1", "TURKANA", linksFromTxt.get(224),224));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.familyradiologo,"FAMILY RADIO","103.9","NAIROBI",linksFromTxt.get(28),28));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.mbciradiologo,"MBCI RADIO","89.5","NAKURU",linksFromTxt.get(33),33));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.jesusislordradiologo,"JESUSISLORD RADIO","105.3","NAKURU",linksFromTxt.get(29),29));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.theupperroomlogo,"THE UPPERROOM FM","--.--","NAIROBI",linksFromTxt.get(57),57));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.wauminilogo,"RADIO WAUMINI","88.3","NAIROBI",linksFromTxt.get(45),45));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.bibiliahusemalogo,"BIBILIA HUSEMA FM","96.7","NAIROBI",linksFromTxt.get(46),46));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.barakafmlogo,"BARAKA FM","95.5","MOMBASA",linksFromTxt.get(51),51));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.radioinjililogo,"RADIO INJILI","103.7","KERICHO",linksFromTxt.get(52),52));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.pearlradiologo,"PEARL RADIO","96.9","NAIROBI",linksFromTxt.get(59),59));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.imaniradiologo,"IMANI RADIO","88.8","KITALE",linksFromTxt.get(60),60));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.shilohradiologo,"SHILOH RADIO","--.--","NAIROBI",linksFromTxt.get(96),96));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.christianradiologo560,"560 CHRISTIAN RADIO","560kHz","NAIROBI",linksFromTxt.get(110),110));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.sayakiradiologo,"SAYAKI RADIO","--.--","NYERI",linksFromTxt.get(125),125));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.radiotumainilogo,"RADIO TUMAINI","93.0","NAIROBI",linksFromTxt.get(198),198));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.pgradiologo,"PG RADIO","--.--","KISUMU",linksFromTxt.get(158),158));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.prasieradiologo,"PRAISE RADIO","--.--","NAIROBI",linksFromTxt.get(180),180));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.engelosradiologo,"ENGELOS RADIO","--.--","NAIROBI",linksFromTxt.get(189),189));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.moronigospellogo,"MORONI GOSPEL","--.--","NAIROBI",linksFromTxt.get(182),182));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.ggvfmlogo,"GGV FM","--.--","NAIROBI",linksFromTxt.get(196),196));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.pemiradiologo,"PEMI RADIO","96.1","NAIROBI",linksFromTxt.get(151),151));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.sifafmlogo,"SIFA FM","101.2","MARSABIT",linksFromTxt.get(81),81));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.gracefmlogo,"GRACE FM","--.--","NAIROBI",linksFromTxt.get(171),171));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.mwangazawanenofmlogo,"MWANGAZA WA NENO FM","--.--","NAIROBI",linksFromTxt.get(174),174));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.safinaradiologo,"SAFINA RADIO","--.--","NAIROBI",linksFromTxt.get(178),178));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.sayarefmlogo,"SAYARE FM","--.--","NAIROBI",linksFromTxt.get(179),179));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.mecolfmlogo,"MECOL FM","--.--","NAIROBI",linksFromTxt.get(128),128));
        CategoryPraiseWorshipRadioStationList.add(new RadioStation(R.drawable.radiotumainilogo,"RADIO TUMAINI","93.0","NAIROBI",linksFromTxt.get(198),198));

        //MUSLIM
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.iqrafmlogo,"IQRA FM","95.0","NAIROBI",linksFromTxt.get(34),34));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.radiosalaamlogo,"RADIO SALAAM","90.7","MOMBASA",linksFromTxt.get(67),67));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.radiorahmalogo,"RADIO RAHMA","91.5","MOMBASA",linksFromTxt.get(68),68));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.ebruradiologo, "EBRU RADIO", "89.7", "MANDERA", linksFromTxt.get(222),222));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.iftinfmlogo,"IFTIIN FM","101.9","NAIROBI",linksFromTxt.get(87),87));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.mindimoradiologo,"MINDIMO RADIO","--.--","NAIROBI",linksFromTxt.get(154),154));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.tsfmlogo,"TS FM","--.--","NAIROBI",linksFromTxt.get(192),192));
        CategoryIslamRadioStationList.add(new RadioStation(R.drawable.tusmofmlogo,"TUSMO FM","--.--","NAIROBI",linksFromTxt.get(149),149));

        //COASTAL
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.baharilogo,"BAHARI FM","90.4","MOMBASA",linksFromTxt.get(23),23));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.midzifmlogo,"MIDZI FM","100.5","MALINDI",linksFromTxt.get(123),123));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.msenangufmlogo,"MSENANGU FM","99.5","MOMBASA",linksFromTxt.get(146),146));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.pwanifmlogo,"PWANI FM","103.1","MOMBASA",linksFromTxt.get(35),35));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.sautiyapwanifmlogo,"SAUTIYAPWANI FM","94.2","MOMBASA",linksFromTxt.get(36),36));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.radiokayalogo,"RADIO KAYA","93.1","NAIROBI",linksFromTxt.get(49),49));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.pilipilifmlogo,"PILIPILI FM","99.5","NAIROBI",linksFromTxt.get(63),63));
        CategoryCoastalRadioStationList.add(new RadioStation(R.drawable.lulufmlogo,"LULU FM","91.0","MOMBASA",linksFromTxt.get(58),58));

        //ASIAN
        CategoryAsianStationList.add(new RadioStation(R.drawable.eastfmlogo,"EAST FM","106.3","NAIROBI",linksFromTxt.get(40),40));
        CategoryAsianStationList.add(new RadioStation(R.drawable.soundasialogo,"SOUNDASIA RADIO","88.0","NAIROBI",linksFromTxt.get(43),43));
        CategoryAsianStationList.add(new RadioStation(R.drawable.radio44logo,"RADIO 44","91.6","NAIROBI",linksFromTxt.get(124),124));

        //EDM
        CategoryEdmStationList.add(new RadioStation(R.drawable.lionafriqradiologo,"LIONAFRIQ RADIO","--.--","NAIROBI",linksFromTxt.get(61),61));
        CategoryEdmStationList.add(new RadioStation(R.drawable.soundcityradiologo,"SOUNDCITY RADIO","88.5","NAIROBI",linksFromTxt.get(1),1));
        CategoryEdmStationList.add(new RadioStation(R.drawable.elwaicenterfmlogo,"ELWAI CENTER FM","--.--","SUSWA",linksFromTxt.get(133),133));
        CategoryEdmStationList.add(new RadioStation(R.drawable.capitalfmlogo, "CAPITAL FM", "98.4", "NAIROBI", linksFromTxt.get(54),54));
        CategoryEdmStationList.add(new RadioStation(R.drawable.rogueradiologo,"ROGUE RADIO","--.--","NAIROBI",linksFromTxt.get(26),26));
        CategoryEdmStationList.add(new RadioStation(R.drawable.nworksradiologo,"NWORKS RADIO","--.--","NAIROBI",linksFromTxt.get(193),193));


        //CATHOLIC
        CategoryCatholicStationList.add(new RadioStation(R.drawable.varchradiologo,"VARCH RADIO","--.--","ELDORET",linksFromTxt.get(130),130));
        CategoryCatholicStationList.add(new RadioStation(R.drawable.radio_maria,"RADIO MARIA","90.0","NAIROBI",linksFromTxt.get(72),72));
        CategoryCatholicStationList.add(new RadioStation(R.drawable.wauminilogo,"RADIO WAUMINI","88.3","NAIROBI",linksFromTxt.get(45),45));
        CategoryCatholicStationList.add(new RadioStation(R.drawable.radiotumainilogo,"RADIO TUMAINI","93.0","NAIROBI",linksFromTxt.get(198),198));
        CategoryCatholicStationList.add(new RadioStation(R.drawable.upendoradiologo,"UPENDO RADIO","89.4","ELDORET",linksFromTxt.get(73),73));
        //POKOT
        CategoryPokotStationList.add(new RadioStation(R.drawable.kokwofmlogo,"KOKWO FM","100.1","KAPENGURIA",linksFromTxt.get(106),106));
        CategoryPokotStationList.add(new RadioStation(R.drawable.northriftradiologo,"NORTHRIFT RADIO","104.5","KAPENGURIA",linksFromTxt.get(102),102));
        //REGGAE
        CategoryReggaeStationList.add(new RadioStation(R.drawable.rasstyleradiologo,"RASSTYLE RADIO","--.--","NAIROBI",linksFromTxt.get(164),104));
        CategoryReggaeStationList.add(new RadioStation(R.drawable.vybesradiologo,"VYBES RADIO","104.5","NAIROBI",linksFromTxt.get(172),172));
        CategoryReggaeStationList.add(new RadioStation(R.drawable.ghettoradiologo,"GHETTO RADIO","89.5","NAIROBI",linksFromTxt.get(12),12));
        CategoryReggaeStationList.add(new RadioStation(R.drawable.icedfmlogo,"ICED RADIO","--.--","NAIROBI",linksFromTxt.get(138),138));
        CategoryReggaeStationList.add(new RadioStation(R.drawable.nrg_dancehall_logo, "NRG DANCEHALL", "--.--", "NAIROBI", linksFromTxt.get(212),212));


        //ETHIOPIAN
        CategoryEthiopianStationList.add(new RadioStation(R.drawable.edenmediaradiologo,"EDEN MEDIA","--.--","NAIROBI",linksFromTxt.get(162),162));


        //ADDING CATEGORIES
        CategoryList.add(new Category("HOT & TRENDING "+"("+CategoryHotRadioStationList.size()+")",CategoryHotRadioStationList));
        CategoryList.add(new Category("LIVE MIXXES "+"("+CategoryMixesRadioStationList.size()+")",CategoryMixesRadioStationList));
        CategoryList.add(new Category("CONTEMPORARY CHRISTIAN "+"("+CategoryChristianContemporaryRadioStationList.size()+")",CategoryChristianContemporaryRadioStationList));
        CategoryList.add(new Category("EDM/AMAPIANO "+"("+CategoryEdmStationList.size()+")",CategoryEdmStationList));
        CategoryList.add(new Category("KIKUYU "+"("+CategoryKikuyuRadioStationList.size()+")",CategoryKikuyuRadioStationList));
        CategoryList.add(new Category("KALENJIN "+"("+CategoryKalenjinRadioStationList.size()+")",CategoryKalenjinRadioStationList));
        CategoryList.add(new Category("PRAISE & WORSHIP "+"("+CategoryPraiseWorshipRadioStationList.size()+")",CategoryPraiseWorshipRadioStationList));
        CategoryList.add(new Category("LUO "+"("+CategoryLuoRadioStationList.size()+")",CategoryLuoRadioStationList));
        CategoryList.add(new Category("MAASAI "+"("+CategoryMaasaiRadioStationList.size()+")",CategoryMaasaiRadioStationList));
        CategoryList.add(new Category("LUHYA "+"("+CategoryLuhyaRadioStationList.size()+")",CategoryLuhyaRadioStationList));
        CategoryList.add(new Category("KAMBA "+"("+CategoryKambaRadioStationList.size()+")",CategoryKambaRadioStationList));
        CategoryList.add(new Category("TALKS "+"("+CategoryTalksRadioStationList.size()+")",CategoryTalksRadioStationList));
        CategoryList.add(new Category("SPORTS "+"("+CategorySportRadioStationList.size()+")",CategorySportRadioStationList));
        CategoryList.add(new Category("KISII "+"("+CategoryKisiiRadioStationList.size()+")",CategoryKisiiRadioStationList));
        CategoryList.add(new Category("SEVENTH DAY ADEVENTIST "+"("+CategorySdaRadioStationList.size()+")",CategorySdaRadioStationList));
        CategoryList.add(new Category("ISLAM "+"("+CategoryIslamRadioStationList.size()+")",CategoryIslamRadioStationList));
        CategoryList.add(new Category("MERU "+"("+CategoryMeruRadioStationList.size()+")",CategoryMeruRadioStationList));
        CategoryList.add(new Category("CATHOLIC "+"("+CategoryCatholicStationList.size()+")",CategoryCatholicStationList));
        CategoryList.add(new Category("REGGAE "+"("+CategoryReggaeStationList.size()+")",CategoryReggaeStationList));
        CategoryList.add(new Category("COASTAL REGION "+"("+CategoryCoastalRadioStationList.size()+")",CategoryCoastalRadioStationList));
        CategoryList.add(new Category("ASIAN/HINDU "+"("+CategoryAsianStationList.size()+")",CategoryAsianStationList));
        CategoryList.add(new Category("POKOT "+"("+CategoryPokotStationList.size()+")",CategoryPokotStationList));
        CategoryList.add(new Category("ETHIOPIAN "+"("+CategoryEthiopianStationList.size()+")",CategoryEthiopianStationList));

    }
}