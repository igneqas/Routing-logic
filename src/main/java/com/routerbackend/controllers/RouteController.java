package com.routerbackend.controllers;

import com.routerbackend.core.OsmNodeNamed;
import com.routerbackend.core.OsmTrack;
import com.routerbackend.core.RoutingContext;
import com.routerbackend.core.RoutingEngine;
import com.routerbackend.request.IRequestHandler;
import com.routerbackend.request.RequestHandler;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@RestController
public class RouteController {
    private RoutingEngine routingEngine = null;


    @GetMapping(value = "/route")
    @ResponseBody
    @CrossOrigin(origins = "http://localhost:3000")
    public String getRoute (@RequestParam("profile") String profile, @RequestParam("lonlats") String lonlats, @RequestParam("nogos") String nogos, @RequestParam("format") String format, @RequestParam(value = "alternativeidx", required = false) String alternativeIdx) {
        if(profile.isEmpty())
            return "Provide a profile.";
        if(lonlats.isEmpty())
            return "Provide lonlats.";
        if(format.isEmpty())
            return "Provide format.";
        IRequestHandler handler = new RequestHandler();
        RoutingContext rc = handler.readRoutingContext(profile, nogos, alternativeIdx);
        List<OsmNodeNamed> wplist = handler.readWayPointList(lonlats);
//        if (wplist.size() < 10) {
//            SuspectManager.nearRecentWps.add(wplist);
//        }
//        for (Map.Entry<String, String> e : params.entrySet()) {
//            if ("timode".equals(e.getKey())) {
//                rc.turnInstructionMode = Integer.parseInt(e.getValue());
//            } else if ("heading".equals(e.getKey())) {
//                rc.startDirection = Integer.valueOf(Integer.parseInt(e.getValue()));
//                rc.forceUseStartDirection = true;
//            } else if (e.getKey().startsWith("profile:")) {
//                if (rc.keyValues == null) {
//                    rc.keyValues = new HashMap<String, String>();
//                }
//                rc.keyValues.put(e.getKey().substring(8), e.getValue());
//            } else if (e.getKey().equals("straight")) {
//                String[] sa = e.getValue().split(",");
//                for (int i = 0; i < sa.length; i++) {
//                    int v = Integer.valueOf(sa[i]);
//                    if (wplist.size() > v) wplist.get(v).direct = true;
//                }
//            }
//        }
        routingEngine = new RoutingEngine(wplist, rc);
        routingEngine.quite = true;
        routingEngine.doRun();
        if (routingEngine.getErrorMessage() != null) {
//            writeHttpHeader(bw, HTTP_STATUS_BAD_REQUEST);
//            bw.write(cr.getErrorMessage());
//            bw.write("\n");
        } else {
            OsmTrack track = routingEngine.getFoundTrack();

//            String headers = encodings == null || encodings.indexOf("gzip") < 0 ? null : "Content-Encoding: gzip\n";
//            writeHttpHeader(bw, handler.getMimeType(), handler.getFileName(), headers, HTTP_STATUS_OK);
            if (track != null) {
//                if (headers != null) // compressed
//                {
//                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                    Writer w = new OutputStreamWriter(new GZIPOutputStream(baos), "UTF-8");
//                    w.write(handler.formatTrack(track));
//                    w.close();
//                    bw.flush();
//                    clientSocket.getOutputStream().write(baos.toByteArray());
//                } else {
                    return handler.formatTrack(track);
//                }
            }
        }
        System.out.println(rc.localFunction);
        System.out.println(wplist);
        return "";
    }
//    public @ResponseBody
//    Iterable<Books> getAll () {
//        System.out.println(booksRepository.findAll());
//        return booksRepository.findAll();
//    }
}
